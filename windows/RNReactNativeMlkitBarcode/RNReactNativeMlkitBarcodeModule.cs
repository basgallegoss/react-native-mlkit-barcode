using ReactNative.Bridge;
using System;
using System.Collections.Generic;
using Windows.ApplicationModel.Core;
using Windows.UI.Core;

namespace React.Native.Mlkit.Barcode.RNReactNativeMlkitBarcode
{
    /// <summary>
    /// A module that allows JS to share data.
    /// </summary>
    class RNReactNativeMlkitBarcodeModule : NativeModuleBase
    {
        /// <summary>
        /// Instantiates the <see cref="RNReactNativeMlkitBarcodeModule"/>.
        /// </summary>
        internal RNReactNativeMlkitBarcodeModule()
        {

        }

        /// <summary>
        /// The name of the native module.
        /// </summary>
        public override string Name
        {
            get
            {
                return "RNReactNativeMlkitBarcode";
            }
        }
    }
}
