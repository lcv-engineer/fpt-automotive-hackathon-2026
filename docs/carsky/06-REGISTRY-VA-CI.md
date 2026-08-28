# 06 — Registry, build image và CI

---

## 1. Registry `registry.hackathon-2.carsky.io`

### Xác thực — credential RIÊNG, không phải API key

Registry đòi cặp user/token riêng. **API key CarSky không dùng được** (thử 06/08:
`Basic`, `Bearer`, ẩn danh đều `401`).

| Nơi | Tên biến |
|---|---|
| `backend/.env` | `CARSKY_REGISTRY_USER` / `CARSKY_REGISTRY_TOKEN` |
| GitHub Secrets | `CARSKY_REGISTRY_USERNAME` / `CARSKY_REGISTRY_PASSWORD` |

⚠️ Khi đặt secret bằng CLI, nhớ `.Trim()` — token dính ký tự xuống dòng làm
`docker login` fail `401` với thông báo chẳng liên quan.

### Tra registry bằng API v2 chuẩn

```bash
curl -u "$USER:$TOKEN" https://registry.hackathon-2.carsky.io/v2/viva/viva-asr/tags/list
```

```bash
curl -u "$USER:$TOKEN" -H "Accept: application/vnd.oci.image.index.v1+json" \
  https://registry.hackathon-2.carsky.io/v2/viva/viva-asr/manifests/<tag>
```

Lấy ngày build và biến env nướng trong image: đi từ index → manifest amd64 →
config blob. Hữu ích để biết một image thật sự cũ đến đâu.

---

## 2. Kiến trúc — build đa nền tảng

**Cluster chạy arm64.** Tài liệu tutorial của nền tảng ghi: *image x86_64 chạy qua
QEMU có thể lỗi ngẫu nhiên*, và khuyên build `--platform linux/arm64`. Android VM
cũng `aarch64` (`ro.product.cpu.abi = arm64-v8a`).

Luôn build **đa kiến trúc** (`linux/amd64,linux/arm64`) và thêm
`docker/setup-qemu-action` — thiếu nó thì stage `runtime` (có `pip install`) không
build được dưới arm64 trên runner x86. Build cả hai kiến trúc mất **~5 phút**.

> ⚠️ Kiến trúc **không** phải nguyên nhân của lỗi Redeploy (đã loại trừ — xem
> [05 §4](05-VONG-DOI-DEPLOYMENT.md)). Nhưng cứ giữ đa kiến trúc: bản duy nhất từng
> chạy được là đa kiến trúc, và thu hẹp lại là đánh cược không có lợi.

---

## 3. Digest, không phải tag

Node ghim image bằng **digest**. Push đè lên một tag rồi restart sẽ kéo lại đúng
image cũ — pipeline chạy mà không đổi gì. **Luôn dùng dạng `@sha256:…`**:

```
registry.hackathon-2.carsky.io/viva/viva-asr@sha256:63c2c56a...
```

✅ Nền tảng **chấp nhận dạng digest** — đã xác nhận 04/08 với 22/22 node `Running`.
Với `viva-asr`, digest khoá luôn cả model đã convert nằm trong image.
Bằng chứng: `evidence/carsky/v7-manifest.txt` + `v7-asr-node-phases.json`.

---

## 4. Hai cái bẫy trong build-args

| Bẫy | Hậu quả |
|---|---|
| **`ASR_MODEL_NAME` không được lấy từ `tag`** | Bản đầu của workflow viết `ASR_MODEL_NAME=${{ inputs.tag }}`, gộp "tên bản phát hành" với "nhãn model" làm một. Push với `tag=0.2.0` làm image mang `ASR_MODEL_NAME=0.2.0`, khiến `/health` và header `X-Asr-Model` khai một tên model **không tồn tại**. Phải tách thành input riêng |
| **Phải truyền `VIVA_GIT_COMMIT=${{ github.sha }}`** | `Dockerfile` khai `ARG VIVA_GIT_COMMIT=unknown` rồi nướng vào ENV; không truyền thì mọi image đẩy lên đều mang `unknown` và **không truy ngược về commit được** — đúng thứ mà kỷ luật ghim-digest sinh ra để bảo vệ |

---

## 5. Workflow trong repo

| Workflow | Trigger | Việc |
|---|---|---|
| `carsky-push-asr-image` | `workflow_dispatch` | Build đa kiến trúc + push + **in digest** |
| `carsky-deploy-asr` | `workflow_dispatch` | `PATCH` digest vào node + restart node + chờ `Running` + **rollback** nếu không lên |
| `android-ci` · `asr-ci` · `backend-ci` | `push` + `pull_request` | Chỉ kiểm thử, **không** đụng CarSky |

### `carsky-push-asr-image` — input

| Input | Mặc định | Ý nghĩa |
|---|---|---|
| `tag` | `phowhisper-tiny-int8` | Tag để push (digest vẫn là thứ dùng khi deploy) |
| `hf_model` | `vinai/PhoWhisper-tiny` | Model gốc trên HuggingFace |
| `model_name` | `phowhisper-tiny-int8` | Nhãn model báo cáo ở `/health` và header `X-Asr-Model` |

```bash
gh workflow run carsky-push-asr-image -f tag=<moi> -f hf_model=vinai/PhoWhisper-tiny -f model_name=phowhisper-tiny-int8
```

### `carsky-deploy-asr` — input

| Input | Mặc định | Ý nghĩa |
|---|---|---|
| `image` | (bắt buộc) | Image đầy đủ, **nên dùng dạng digest** |
| `room` | `v37aa3knc6t1embelr5yi` | Room đang chạy |
| `node` | `b8eada00-d137-4fdc-a131-2197b1d1356b` | Node `VIVA ASR` |
| `dry_run` | `true` | Chỉ in ra thay đổi sẽ làm, không gọi `PATCH`/restart |

Workflow có bước **chặn image lạ** (từ chối registry khác) và bước **lùi lại image cũ
nếu node không lên**.

⚠️ Nhớ [05 §3](05-VONG-DOI-DEPLOYMENT.md): `PATCH` + `restart` **chỉ đổi được image**,
không áp được `env` mới cho deployment đang chạy.

---

## 6. Vì sao workflow CarSky là `workflow_dispatch`, đừng bật `on: push`

Bốn lý do, lý do cuối là nặng nhất:

1. **Quota 2 deployment** — chạy mỗi lần merge là đụng trần ngay.
2. **Đụng room demo** giữa lúc tổng duyệt.
3. **Giai đoạn freeze** trước demo cần môi trường đứng yên nhất.
4. 🔴 **Đổi image bắt buộc xoá-dựng-lại, mà việc đó xoá luôn APK và cấu hình mạng
   trên VM Android.** Một PR sửa docs mà tự động làm việc đó là thảm hoạ.

### "CD lên CarSky" giao được gì

| Việc | Tự động được? | Vì sao |
|---|---|---|
| Deploy/redeploy blueprint | ✅ | `POST /deployments` + `DELETE /deployments/{roomId}`, chỉ cần API key trong Secrets |
| Cập nhật container `viva-asr` | ✅ | Có `carsky-push-asr-image` + `carsky-deploy-asr` |
| **Cài APK lên node Android** | 🚫 | Cần `adb`, mà `adb-exec`/`shell` chết vì Conduit. **Không có đường HTTPS nào thay thế** |

⇒ CD lên CarSky **không giao được APK** — tức là không giao được thứ cả demo phụ
thuộc vào. Bước cài APK vẫn phải bấm tay qua widget `IVI ADB`.

**Đề xuất khi Conduit được bật:** thêm bước cài APK vào workflow, và lúc đó mới đáng
bàn tới `on: push` cho nhánh release.
