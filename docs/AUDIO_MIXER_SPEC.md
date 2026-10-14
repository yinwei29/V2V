# Stereo PCM 混音 POC

M1 來源及譯音 fixture 均為 48kHz mono PCM16。輸出 48kHz stereo PCM16，每段 960 mono samples / 20ms。兩個 fixture 為原創 2 秒 440Hz / 660Hz 正弦波，振幅 25%，非人聲／非 TTS。

SPLIT：L=gT*T，R=gO*O，可交換。
TRANSLATION_ONLY：L=R=gT*T；原聲實際係數為 0，DataStore 保留其他模式原聲增益，滑桿禁用。
MIX：L=R=limiter(gO*O+gT*T)。

Limiter：abs(x)<=0.8 時 identity，以上為 0.8+0.2*tanh((abs(x)-0.8)/0.2)，保留 sign，無硬削波。PCM16 轉換幅度乘 32767。

增益、模式、左右交換一起形成 2×2 routing matrix，每 sample 以 50ms ramp 漸變；快速再次變更由目前係數到新目標，不重新產生或排隊舊音訊。初始由 0 fade-in。聲道可隔離屬軟體 PCM 性質，不證明實際 Bluetooth/HFP 輸出。

Stereo downmix 定義 (L+R)*0.3535534，即平均後再 3dB headroom；反相素材仍會相消，需來源試聽。M1 未接受其他格式檔案，不做假重採樣。M3 真實 TTS 異格式需抗混疊 resampler。

原聲 PCM 先送 AsrProbe，gO/gT 不改其樣本或 RMS。外部來源永遠不進原聲輸出；其所有完整 mixer 控制禁用。測試詳見 StereoMixerTest。
