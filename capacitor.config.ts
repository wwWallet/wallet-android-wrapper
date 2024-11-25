import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'io.yubicolabs.funke_explorer',
  appName: 'wallet-frontend',
  webDir: 'funke-wallet-frontend/build',
  android: {
    path: 'webview',
    useLegacyBridge: true,
    buildOptions: {
      releaseType: 'APK'
    }
  }
};

export default config;
