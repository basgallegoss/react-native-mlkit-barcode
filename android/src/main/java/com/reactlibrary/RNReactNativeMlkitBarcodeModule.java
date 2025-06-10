
package com.reactlibrary;


import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

public class RNReactNativeMlkitBarcodeModule extends ReactContextBaseJavaModule {
  private static final int BARCODE_SCAN_REQUEST = 12345;
  private static Promise scanPromise;

  private final ReactApplicationContext reactContext;

  public RNReactNativeMlkitBarcodeModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @NonNull
  @Override
  public String getName() {
    return "RNReactNativeMlkitBarcode";
  }

  @ReactMethod
  public void scanBarcode(Promise promise) {
    Activity currentActivity = getCurrentActivity();
    if (currentActivity == null) {
      promise.reject("NO_ACTIVITY", "No activity attached");
      return;
    }
    scanPromise = promise;
    Intent intent = new Intent(currentActivity, BarcodeScannerActivity.class);
    currentActivity.startActivityForResult(intent, BARCODE_SCAN_REQUEST);
  }

  // Este método debe ser llamado desde la MainActivity para recibir el resultado del escaneo
  public static void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == BARCODE_SCAN_REQUEST && scanPromise != null) {
      if (resultCode == Activity.RESULT_OK && data != null) {
        String barcode = data.getStringExtra("barcode");
        scanPromise.resolve(barcode);
      } else {
        scanPromise.reject("CANCELLED", "Scan cancelled or no data");
      }
      scanPromise = null;
    }
  }
}
