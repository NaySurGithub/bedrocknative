package net.bedrocknative.net.auth.window;

import java.util.Map;

final class HelperBuild {
   static final Map<String, String> FILES = Map.ofEntries(
      Map.entry("AuthWebView.exe", "03135121f5f66039f68ca37de79ad48ad70e50b861bac75672a501469118f990"),
      Map.entry("AuthWebView.exe.config", "9511fa9582bfd1f37a822b0f9a0cf146d46c4eacc7ee54e296799381ca43b6b2"),
      Map.entry("Microsoft.Web.WebView2.Core.dll", "11e88b2ca921e5c88f64567f11bd83cbc396c10365d40972f3359fcc7965d197"),
      Map.entry("Microsoft.Web.WebView2.WinForms.dll", "e4f695b72f99024e3ee5d5f26a367e664f4e120bd5d90aa87a8bc0509c365ec8"),
      Map.entry("runtimes/win-x64/native/WebView2Loader.dll", "465a7ddfb3a0da4c3965daf2ad6ac7548513f42329b58aebc337311c10ea0a6f"),
      Map.entry("runtimes/win-arm64/native/WebView2Loader.dll", "77152af4472dc7741901ba69ce3a670992546eb2f5eda3db7fee135ee0037de0")
   );

   private HelperBuild() {
   }
}
