# CarSky — Bộ tài liệu vận hành nền tảng

> **Phạm vi:** mọi thứ đội VIVA đã học được về nền tảng CarSky Hackathon-2, từ
> 31/07/2026 đến 20/08/2026. Đây là bản **hợp nhất và sắp theo việc cần làm**.
>
> **Quy ước tin cậy — áp dụng cho mọi file trong thư mục này:**
>
> | Ký hiệu | Nghĩa |
> |---|---|
> | ✅ | Đã gọi/chạy thật, có phản hồi hoặc log đã đọc. Có đường dẫn evidence hoặc file nguồn |
> | ⚠️ | Đúng nhưng có điều kiện/giới hạn phải khai kèm |
> | ❌ | **CHƯA THỬ** hoặc đã thử và không được. Không suy đoán thay |
> | 🚫 | Đã xác nhận là **không làm được** bằng đường đó |
>
> Chỗ nào chưa kiểm thì ghi rõ. Giá trị của bộ tài liệu này nằm ở chỗ **tin được**,
> không phải ở chỗ đầy đủ hình thức.

---

## Đọc theo mục đích

| Bạn cần làm gì | Đọc file nào |
|---|---|
| Hiểu CarSky là gì, các khái niệm device/room/blueprint/node/pin | [01 — Khái niệm & kiến trúc nền tảng](01-KHAI-NIEM-VA-KIEN-TRUC.md) |
| Gọi REST API, biết endpoint nào sống endpoint nào chết | [02 — API reference](02-API-REFERENCE.md) |
| Biết room VIVA có những node nào, tín hiệu chạy đường nào | [03 — Blueprint & node](03-BLUEPRINT-VA-NODE.md) |
| App không gọi được ASR node, `eth1` không có IP | [04 — Mạng trong room](04-MANG-TRONG-ROOM.md) |
| Deploy/redeploy/restart, đổi image container | [05 — Vòng đời deployment](05-VONG-DOI-DEPLOYMENT.md) |
| Build & push image lên registry, chạy workflow CI | [06 — Registry & CI](06-REGISTRY-VA-CI.md) |
| Đưa APK lên máy ảo Android, dùng widget ADB | [07 — APK, Artifact & ADB](07-APK-ARTIFACT-ADB.md) |
| Chuẩn bị trước một phiên làm việc / demo | [08 — Preflight & khôi phục](08-PREFLIGHT-VA-KHOI-PHUC.md) |
| Tra một triệu chứng lỗi đã gặp | [09 — Sự cố, giới hạn & câu hỏi treo](09-SU-CO-VA-GIOI-HAN.md) |

## Bảng tra nhanh — ID và địa chỉ

Nguồn: [`docs/backend-docs/carsky-runbook.md`](../backend-docs/carsky-runbook.md) §0,
[`vong2/37-RUNBOOK-PREFLIGHT-CARSKY.md`](../../vong2/37-RUNBOOK-PREFLIGHT-CARSKY.md).

| Thứ | Giá trị |
|---|---|
| API base | `https://hackathon-2.carsky.io/api/v1` |
| Xác thực | header `x-api-key: <CARSKY_API_KEY>` (hoặc `Authorization: Bearer <key>`) |
| Registry | `registry.hackathon-2.carsky.io` |
| Device `VIVA` (room demo) | `v37aa3knc6t1embelr5yi` |
| Device `VIVA (Copy)` | `wcmfnwigjse4hv9r8s0e3` |
| Blueprint đang dùng | `6deadb05-c856-4dab-976b-432b0fac0658` |
| Node `VIVA ASR` (container) | `b8eada00-d137-4fdc-a131-2197b1d1356b` |
| Node `IVI - Android` (skycraft) | `cf7fe8d1-0a9c-48fe-9b59-573e3747f2cb` |
| Node `VCU` (script) | `faa07ae4-8953-468a-a5b6-4304cb52a6c9` |
| Node `IVI Gateway` (script) | `n-4e60c4fe-350e-4333-9e50-0bcd5596a609` |
| Pin `eth` của node ASR | `8pzTH3XYHO81KOqn3ygiD` |
| Package app | `com.sopa.viva_automotive.mock` (mock) · `com.sopa.viva_automotive` (real) |

⚠️ **Có HAI blueprint trùng tên `VIVA-deploy-clone-0803`.** Bản
`7175eb09-8d15-451e-a26f-aec1f60e667c` **không có node ASR** — sửa nhầm vào đó là
sửa vào hư không mà không báo lỗi. Bản đúng là `6deadb05-…`.

⚠️ `CARSKY_DEVICE_ID` trong `backend/.env` trỏ **VIVA 2**, không phải room demo.

## Cấu hình và bí mật

Biến môi trường đọc từ `backend/.env` (đã gitignore, mẫu ở `backend/.env.example`):

```
CARSKY_API_KEY · CARSKY_BASE_URL · CARSKY_ROOM_ID
CARSKY_REGISTRY · CARSKY_REGISTRY_USER · CARSKY_REGISTRY_TOKEN
```

Trên GitHub Actions, credential registry nằm ở secret `CARSKY_REGISTRY_USERNAME`
/ `CARSKY_REGISTRY_PASSWORD`.

🚫 Không commit token/API key/`openapi.json` (128 KB, nội bộ nền tảng — thể lệ 3.6)
vào repo.

## Nguồn gốc của bộ tài liệu này

Ba file gốc vẫn được giữ nguyên và vẫn là nguồn chi tiết nhất theo trình tự thời gian:

| File gốc | Vai trò |
|---|---|
| [`docs/backend-docs/carsky-api.md`](../backend-docs/carsky-api.md) | Nhật ký khám phá API theo ngày, có bối cảnh vì sao từng kết luận được rút ra |
| [`docs/backend-docs/carsky-runbook.md`](../backend-docs/carsky-runbook.md) | Sổ tay vận hành đã chắt lọc (07–08/08) |
| [`vong2/35-NHAT-KY-CARSKY-19-08.md`](../../vong2/35-NHAT-KY-CARSKY-19-08.md) | Phiên 19/08 — bản `real`, USB image, root cause VHAL |
| [`vong2/37-RUNBOOK-PREFLIGHT-CARSKY.md`](../../vong2/37-RUNBOOK-PREFLIGHT-CARSKY.md) | Checklist trước phiên và khôi phục sau restart |
| [`docs/platform/Car-Sky-Platform.html`](../platform/Car-Sky-Platform.html) | **Tài liệu chính thức của nền tảng**, 39 mục. Là nguồn đúng khi `openapi.json` mâu thuẫn |

Thư mục này **không thay thế** các file trên; nó sắp xếp lại kiến thức cho người
mới và giữ các mâu thuẫn đã phát hiện ở một chỗ.
