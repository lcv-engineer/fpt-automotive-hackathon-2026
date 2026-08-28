# Tài liệu VIVA — mục lục

Hai bộ tài liệu chính, viết cho người mới vào việc và cho người phải vận hành lúc gấp:

| Bộ | Nội dung |
|---|---|
| 🖥️ **[Hệ thống VIVA](he-thong/00-INDEX.md)** | Kiến trúc, code, voice pipeline, NLU, SafetyGuard, service ASR, harness, embedded, build/CI, bằng chứng |
| ☁️ **[Nền tảng CarSky](carsky/00-INDEX.md)** | API, blueprint/node, mạng trong room, deployment, registry/CI, APK & ADB, preflight, sự cố & giới hạn |

Mỗi bộ có `00-INDEX.md` riêng với bảng "đọc theo mục đích".

---

## Tra nhanh — tôi cần làm gì bây giờ?

| Tình huống | Đi đâu |
|---|---|
| Mới vào dự án, muốn hiểu tổng thể | [Hệ thống 01 — Kiến trúc tổng quan](he-thong/01-KIEN-TRUC-TONG-QUAN.md) |
| Không tìm thấy code | [Hệ thống 02 — Bản đồ repo](he-thong/02-BAN-DO-REPO.md) |
| Sắp vào một phiên làm việc trên CarSky | [CarSky 08 — Preflight & khôi phục](carsky/08-PREFLIGHT-VA-KHOI-PHUC.md) |
| Đang gặp lỗi, cần tra triệu chứng | [CarSky 09 — Tra sự cố](carsky/09-SU-CO-VA-GIOI-HAN.md) |
| Cần build APK đưa lên Device | [CarSky 07 — APK, Artifact & ADB](carsky/07-APK-ARTIFACT-ADB.md) · [Hệ thống 09 — Build/CI](he-thong/09-BUILD-TEST-CI.md) |
| Cần trích số liệu vào báo cáo | [Hệ thống 10 — Quan sát & bằng chứng](he-thong/10-QUAN-SAT-VA-BANG-CHUNG.md) |
| Không rõ một thuật ngữ | [`vong2/33-THUAT-NGU-GIAI-THICH.md`](../vong2/33-THUAT-NGU-GIAI-THICH.md) · [Hệ thống 11](he-thong/11-THUAT-NGU.md) |

---

## Các thư mục còn lại trong `docs/`

```text
architecture/   VIVA-VOICE-BRAIN-BODY.md — tai lieu kien truc chuan (baseline 20/08)
decisions/      ADR 001 (Voice/Brain/Body) · ADR 002 (constrained LLM planner)
backend-docs/   carsky-api.md (nhat ky kham pha) · carsky-runbook.md · v6-viva-asr.md
dbc/            DBC/VSS that export tu CarSky — ban duy nhat trong repo
platform/       Car-Sky-Platform.html — tai lieu chinh thuc cua nen tang (39 muc)
btc/            The le, terms, webinar, template cua ban to chuc
bai-nop/        Ban nop Vong 1 va Vong 2
bao-cao/        Bao cao tien do gui mentor
nhat-ky/        Nhat ky cong viec, log tin nhan BTC/mentor
nghien-cuu/     Nghien cuu tham chieu (ViVi cua VinFast)
superpowers/    Spec va implementation plan
```

Kế hoạch, contract và kịch bản demo theo vòng thi nằm ở `vong2/` và `vong3/`.
`vong2/03-contracts.md` là **contract sống**, không phải tài liệu lịch sử.

---

## Quy ước tin cậy dùng chung

| Ký hiệu | Nghĩa |
|---|---|
| ✅ | Đã chạy thật, có evidence hoặc phản hồi đã đọc |
| 🟡 | Có trong code, mới kiểm bằng unit test / emulator / mock |
| ⚠️ | Đúng nhưng có điều kiện phải khai kèm |
| ❌ / 🚫 | Chưa thử, hoặc đã xác nhận không làm được |

**Không viết vào hai bộ tài liệu này thứ chưa kiểm.** Giá trị của chúng nằm ở chỗ
tin được — chỗ nào chưa biết thì ghi rõ là chưa biết, kèm cách kiểm.
