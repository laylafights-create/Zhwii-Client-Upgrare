Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
[Guid("5CDF2C82-841E-4546-9722-0CF74078229A"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IAudioEndpointVolume {
  int f0(); int f1(); int f2(); int f3();
  int SetMasterVolumeLevelScalar(float fLevel, Guid ctx);
  int f5();
  int GetMasterVolumeLevelScalar(out float pfLevel);
}
[Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IMMDevice {
  int Activate(ref Guid id, int clsCtx, int activationParams, out IAudioEndpointVolume aev);
}
[Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IMMDeviceEnumerator {
  int f0();
  int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice endpoint);
}
[ComImport, Guid("BCDE0395-E52F-49B8-A0A1-5C4E0C2A5C5E")] class MMDeviceEnumerator { }
public class ZVol {
  static IAudioEndpointVolume Ep() {
    IMMDeviceEnumerator en = new MMDeviceEnumerator() as IMMDeviceEnumerator;
    IMMDevice d; en.GetDefaultAudioEndpoint(0, 1, out d);
    Guid g = typeof(IAudioEndpointVolume).GUID; IAudioEndpointVolume v;
    d.Activate(ref g, 23, 0, out v); return v;
  }
  public static float Get() { float f; Ep().GetMasterVolumeLevelScalar(out f); return f; }
  public static void Set(float v) { Ep().SetMasterVolumeLevelScalar(v, Guid.Empty); }
}
public class ZKey {
  [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, int flags, int extra);
  public static void Press(byte vk) { keybd_event(vk, 0, 0, 0); keybd_event(vk, 0, 2, 0); }
}
"@
