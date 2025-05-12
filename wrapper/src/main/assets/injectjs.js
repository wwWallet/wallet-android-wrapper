/**
 * script to be injected by the webview to override navigator.credentials and call bridge method instead.
 *
 * Please include this as a string, replacing $JAVASCRIPT_BRIDGE with the name of the android code injected into
 * the webview and optionally $JAVASCRIPT_VISUALIZE_INJECTION to add an 'a' tag to the webview visualizing successful
 * injection.
 */

// check if replaced in Android: if not defined, throws an 'ReferenceError'.
JAVASCRIPT_BRIDGE

// optionally replaced in android: if not defined no visualization
visualize = (typeof JAVASCRIPT_VISUALIZE_INJECTION) !== 'undefined' ? JAVASCRIPT_VISUALIZE_INJECTION : false

// overwrite console.log before using it
if( typeof JAVASCRIPT_BRIDGE.__real_log__ === 'undefined' ) {
    JAVASCRIPT_BRIDGE.__real_log__ = console.log;
    JAVASCRIPT_BRIDGE.__captured_logs__ = [];

    console.log = (...args) => {
      JAVASCRIPT_BRIDGE.__real_log__(...args);
      JAVASCRIPT_BRIDGE.__captured_logs__.push(String(args));
    };
}

// actually inject the code
JAVASCRIPT_BRIDGE.__injected__ = true
JAVASCRIPT_BRIDGE.__promise_cache__ = {}

// add visualization on page
if (visualize) {
    body = document.getElementsByTagName('body')[0]
    if (body) {
        if (document.getElementById('android-injection-visualization')) {
            console.log('already injected, ignoring this viz')
        } else {
            link = document.createElement("a")
            link.setAttribute('id', 'android-injection-visualization')
            link.setAttribute('href', 'javascript:JAVASCRIPT_BRIDGE.openDebugMenu()')
            link.setAttribute('style', 'position:absolute;top:-0px;right:0;padding:0.5em;z-index:9999999;rotate:180deg;')
            link.innerHTML = '🤖'
            body.appendChild(link)
        }
    } else {
        console.log("No <body> found, skipping visualisation of injection. <a href=\"javascript:visualize_injection()\">retry</a>")
    }
}

// override incoming hint
JAVASCRIPT_BRIDGE.__override_hints = []
JAVASCRIPT_BRIDGE.overrideHints = function(newHints) {
    JAVASCRIPT_BRIDGE.__override_hints = newHints;

    if (visualize) {
        var viz = '👀'
        if (newHints.length == 0) {
            viz = '🤖'
        } else if (newHints[0] == 'security-key'){
            viz = '🗝️'
        } else if (newHints[0] == 'client-device'){
            viz = '📲'
        } else if (newHints[0] == 'emulator'){
            viz = '🥸'
        } else {
           viz = '¿¿'
        }

        document.getElementById('android-injection-visualization').innerHTML = viz
    } else {
        console.log('no viz, no update.')
    }
}

// override functions on navigator
function overrideNavigatorCredentialsWithBridgeCall(method) {
    navigator.credentials[method] = (options) => {
      var uuid = crypto.randomUUID()

      var promise = new Promise((resolve, reject) => {
        JAVASCRIPT_BRIDGE.__promise_cache__[uuid] = {'resolve':resolve, 'reject':reject, 'method': method}

        if (JAVASCRIPT_BRIDGE.__override_hints.length > 0) {
            options.publicKey['hints'] = JAVASCRIPT_BRIDGE.__override_hints
        }

        if (options.publicKey.hasOwnProperty('challenge')) {
            options.publicKey.challenge = __encode(options.publicKey.challenge)
        }

        if (options.publicKey.hasOwnProperty('user') && options.publicKey.user.hasOwnProperty('id')) {
            options.publicKey.user.id = __encode(options.publicKey.user.id)
        }

        if (options.publicKey.hasOwnProperty('allowCredentials')) {
            var allowed = options.publicKey.allowCredentials
            for(var i = 0; i < allowed.length; ++i) {
                allowed[i].id = __encode(allowed[i].id);
            }
        }

        if (options.publicKey.hasOwnProperty('extensions') &&
            options.publicKey.extensions.hasOwnProperty('prf') &&
            options.publicKey.extensions.prf.hasOwnProperty('eval') &&
            options.publicKey.extensions.prf.eval.hasOwnProperty('first') ) {
            options.publicKey.extensions.prf.eval.first = __encode(options.publicKey.extensions.prf.eval.first)
        }

        if (options.publicKey.hasOwnProperty('extensions') &&
            options.publicKey.extensions.hasOwnProperty('prf') &&
            options.publicKey.extensions.prf.hasOwnProperty('evalByCredential') ) {
            for (const k of Object.keys(options.publicKey.extensions.prf.evalByCredential)) {
                if (options.publicKey.extensions.prf.evalByCredential[k].hasOwnProperty('first')) {
                    options.publicKey.extensions.prf.evalByCredential[k].first = __encode(
                        options.publicKey.extensions.prf.evalByCredential[k].first
                    )
                }
                if (options.publicKey.extensions.prf.evalByCredential[k].hasOwnProperty('second')) {
                    options.publicKey.extensions.prf.evalByCredential[k].second = __encode(
                        options.publicKey.extensions.prf.evalByCredential[k].second
                    )
                }
            }
        }

        if (options.publicKey.hasOwnProperty('extensions') &&
            options.publicKey.extensions.hasOwnProperty('prf') &&
            options.publicKey.extensions.prf.hasOwnProperty('eval') &&
            options.publicKey.extensions.prf.eval.hasOwnProperty('second') ) {
            options.publicKey.extensions.prf.eval.second = __encode(options.publicKey.extensions.prf.eval.second)
        }


        if (options.publicKey.hasOwnProperty('extensions') &&
            options.publicKey.extensions.hasOwnProperty('sign') &&
            options.publicKey.extensions.sign.hasOwnProperty('generateKey') &&
            options.publicKey.extensions.sign.generateKey.hasOwnProperty('phData') ) {
            options.publicKey.extensions.sign.generateKey.phData = __encode(options.publicKey.extensions.sign.generateKey.phData)
        }

        if (options.publicKey.hasOwnProperty('extensions') &&
            options.publicKey.extensions.hasOwnProperty('sign') &&
            options.publicKey.extensions.sign.hasOwnProperty('sign') &&
            options.publicKey.extensions.sign.sign.hasOwnProperty('phData') ) {
            options.publicKey.extensions.sign.sign.phData = __encode(options.publicKey.extensions.sign.sign.phData)
        }

        if (options.publicKey.hasOwnProperty('extensions') &&
            options.publicKey.extensions.hasOwnProperty('sign') &&
            options.publicKey.extensions.sign.hasOwnProperty('sign') &&
            options.publicKey.extensions.sign.sign.hasOwnProperty('keyHandleByCredential')) {
          for (const k of Object.keys(options.publicKey.extensions.sign.sign.keyHandleByCredential)) {
              options.publicKey.extensions.sign.sign.keyHandleByCredential[k] = __encode(options.publicKey.extensions.sign.sign.keyHandleByCredential[k]);
          }
        }

        // call bridge, JAVASCRIPT_BRIDGE.__resolve__(uid, ..) or JAVASCRIPT_BRIDGE.__reject__(uid,..) will be called back from android.
        var options_json = JSON.stringify(options, null, 4)
        console.log('options:', options_json)
        JAVASCRIPT_BRIDGE[method](uuid, options_json)
      })

      return promise
    }
}

function __encode(buffer) {
    return btoa(
        Array.from(
            new Uint8Array(buffer),
            function (b) {
                return String.fromCharCode(b);
            }
        ).join('')
    ).replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+${'$'}/, '')
}

function __decode(value) {
    var m = value.length % 4;

    return Uint8Array
        .from(
            atob(
                value
                    .replace(/-/g, '+')
                    .replace(/_/g, '/')
                    .padEnd(
                        value.length + (m === 0 ? 0 : 4 - m), '='
                    )
            ),
            function (c) {
                return c.charCodeAt(0)
            }
        )
        .buffer;
}

function __decode__credentials(result) {
    result.rawId = __decode(result.rawId);
    result.response.clientDataJSON = __decode(result.response.clientDataJSON);
    if (result.response.hasOwnProperty('publicKey')) {
        result.response.publicKey = __decode(result.response.publicKey);
    }
    if (result.response.hasOwnProperty('attestationObject')) {
        result.response.attestationObject = __decode(result.response.attestationObject);
    }
    if (result.response.hasOwnProperty('authenticatorData')) {
        result.response.authenticatorData = __decode(result.response.authenticatorData);
    }
    if (result.response.hasOwnProperty('signature')) {
        result.response.signature = __decode(result.response.signature);
    }
    if (result.response.hasOwnProperty('userHandle')) {
        result.response.userHandle = __decode(result.response.userHandle);
    }

    if (result.hasOwnProperty('clientExtensionResults')) {
        if (result.clientExtensionResults.hasOwnProperty('prf') &&
            result.clientExtensionResults.prf.hasOwnProperty('results')) {
            console.log("PRF FOUND")
            if(result.clientExtensionResults.prf.results.hasOwnProperty('first')) {
                result.clientExtensionResults.prf.results.first = __decode(
                    result.clientExtensionResults.prf.results.first
                );
            }

            if(result.clientExtensionResults.prf.results.hasOwnProperty('second')) {
                result.clientExtensionResults.prf.results.second = __decode(
                    result.clientExtensionResults.prf.results.second
                );
            }
        }

         if (result.clientExtensionResults.hasOwnProperty('largeBlob')) {
             if (result.clientExtensionResults.largeBlob.hasOwnProperty('blob')) {
                 result.clientExtensionResults.largeBlob.blob = __decode(
                    result.clientExtensionResults.largeBlob.blob
                 )
             }
         }

         if (result.clientExtensionResults.hasOwnProperty('sign')) {
            if (result.clientExtensionResults.sign.hasOwnProperty('generatedKey')) {
                if (result.clientExtensionResults.sign.generatedKey.hasOwnProperty('publicKey')) {
                    result.clientExtensionResults.sign.generatedKey.publicKey =
                        __decode(result.clientExtensionResults.sign.generatedKey.publicKey
                    )
                }
                if (result.clientExtensionResults.sign.generatedKey.hasOwnProperty('keyHandle')) {
                    result.clientExtensionResults.sign.generatedKey.keyHandle = __decode(
                        result.clientExtensionResults.sign.generatedKey.keyHandle
                    );
                }
            }

            if (result.clientExtensionResults.sign.hasOwnProperty('signature')) {
                result.clientExtensionResults.sign.signature = __decode(
                    result.clientExtensionResults.sign.signature
                );
            }
         }
    }

    // augment pure json result with functions needed by https://developer.mozilla.org/en-US/docs/Web/API/PublicKeyCredential#instance_properties
    result.getClientExtensionResults = () => result.clientExtensionResults
    result.response.getTransports = () => result.response.transports /// 👀 TODO: CHECK IF SPEC

    return result
}

JAVASCRIPT_BRIDGE.__resolve__ = (uuid, result) => {
    if (uuid in JAVASCRIPT_BRIDGE.__promise_cache__) {
        var promise = JAVASCRIPT_BRIDGE.__promise_cache__[uuid]
        console.log("Promise resolved:", promise.method, uuid)

        if (!promise.method.startsWith('bluetooth')) {
            result = __decode__credentials(result)
        }

        JAVASCRIPT_BRIDGE.__promise_cache__[uuid].resolve(result)

        delete JAVASCRIPT_BRIDGE.__promise_cache__[uuid]
    } else {
        console.log("Promise with id", uuid, "does not exist. Not resolving unknown promise.")
    }
}

JAVASCRIPT_BRIDGE.__reject__ = (uuid, result) => {
    if (uuid in JAVASCRIPT_BRIDGE.__promise_cache__) {
        var promise = JAVASCRIPT_BRIDGE.__promise_cache__ [uuid]
        console.log("Rejected promise", JSON.stringify(promise), "with uuid", uuid, "and result", result)

        JAVASCRIPT_BRIDGE.__promise_cache__[uuid].reject(result)
        delete JAVASCRIPT_BRIDGE.__promise_cache__[uuid]
    } else {
        console.log("Promise with id", uuid, "does not exist. Not rejecting unknown promise.")
    }
}

overrideNavigatorCredentialsWithBridgeCall("create")
overrideNavigatorCredentialsWithBridgeCall("get")

window.PublicKeyCredential = (function () { });
window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable =
    function () {
        return Promise.resolve(false);
    };


// create ble methods
function createBluetoothMethod(method) {
    console.assert (
        typeof JAVASCRIPT_BRIDGE[method+"Wrapped"] !== 'undefined',
        "Associated wrapper function 'JAVASCRIPT_BRIDGE." + method +"Wrapped(promiseUuid,parameter)' not found."
    )

    JAVASCRIPT_BRIDGE[method] = (parameter) => {
        var promiseUuid = crypto.randomUUID()
        if( typeof parameter !== "string") {
            parameter = JSON.stringify(parameter)
        }
        console.log('Calling', method, '(', parameter, ')', "with promise", promiseUuid)

        var promise = new Promise((resolve, reject) => {
            JAVASCRIPT_BRIDGE.__promise_cache__[promiseUuid] = {
                'resolve': resolve,
                'reject': reject,
                'method': method
            }

            JAVASCRIPT_BRIDGE[method+"Wrapped"](promiseUuid, parameter)
        })

        return promise
    }
}

createBluetoothMethod('bluetoothStatus')
createBluetoothMethod('bluetoothTerminate')

createBluetoothMethod('bluetoothCreateServer')
createBluetoothMethod('bluetoothCreateClient')
createBluetoothMethod('bluetoothSendToServer')
createBluetoothMethod('bluetoothSendToClient')
createBluetoothMethod('bluetoothReceiveFromClient')
createBluetoothMethod('bluetoothReceiveFromServer')

window.PublicKeyCredential = (function () { });
window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable =
    function () {
        return Promise.resolve(false);
    };

// call out finalization
console.log('injected!')
