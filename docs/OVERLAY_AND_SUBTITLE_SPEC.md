# M4 預定規格（未實作）

本次 M0/M1 沒有 SYSTEM_ALERT_WINDOW 權限及浮窗，不以主 APP 畫面替代驗收。

M4 必須：WindowManager TYPE_APPLICATION_OVERLAY、56dp 可拖小圈圈、吸附與位置保存、独立字幕窗和面板、窗外觸控通行、通知恢復及停止。拒絕浮窗可用 APP 內字幕。
字幕背景 alpha 與文字／描邊分層；預設黑底 55%、白粗體 18sp、黑描邊 1.5dp、4 行、8dp padding；0–100% 背景不改文字 alpha。同一文字 layout 先描邊後填色。調整／閱讀模式、旋轉邊界、縮放、TalkBack、DataStore schema 和重設均待交付。
T11–T14 不列 PASS，需外部 APP 及繁中／印尼文實測。
