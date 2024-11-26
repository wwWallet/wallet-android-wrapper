FUNKE EXPLORER ANDROID
======================

An Android native application wrapping `https://funke.wwwallet.org`. It is intended as a research project for the
funke and sprintd competition.

Running
-------

You can install the Android app by either [Building](#Building) it, or by exploring the prebuild [release apk](./webview/release/webview-release.apk).

Building
--------

This project uses `gradle` as the combined build tool. Please use the following command to build and install the android
app to all phones attached to the computer issueing this command:

```shell
./gradlew installDebug
```

or

```shell
gradlew.bat installDebug
```

on Windows


Wrapping
--------

This Android application "wraps" the https://funke.wwwallet.org/ website, providing direct interaction with passkeys and
an initial set of bluetooth communication with a verifier. The wrapping happens by loading the website inside an Android
native `WebView` and intercepting interesting js code calls and websides to be loaded.

### Wrapping JS in Kotlin / Android

The aforementioned WebView is used to load the website and intercept not only a locally build version of the website,
but also to catch and wrap incoming javascript calls dealing with passkeys and bluetooth communication. This is done, so
the website can be augmented with native kotlin code directly interacting with hardware: On the one hand for
communicating with password managers, but also for communicating with bluetooth.

### Alternatives to wrapping

Wrapping the website were not the only options considered. Sadly the alternatives were rejected in favor of
wrapping, as explored in the upcoming sections.

#### Kotlin Multiplatform

Kotlin, the language promoted by Google and Jetbrains to write Android Apps, can also be used to write applications
that are running on several platforms. At the time of this writing, those platforms are iOS, Android, Web and iOT
devices.

Exploring this option to run our wallet on Android revealed, that the interoperability between React Native, the
javascript framework used to build the website, cannot be easily replicated inside Kotlin Multiplatform. To use this
alternative, we would need to either port our existing website or start from scratch. While that would open up the
possibility to support more platforms (i.e. Desktops), the effort of translating and reeducation needed for that
development was deemed to high.

#### Native

Where Kotlin Multiplatform is not an option we chose, what about building a native app for Android? This option has the
opportunity to use platform paradigms to confuse the user less about potential webisms introduced by wrapping a website
inside a native app.

But similar to the potential Kotlin Multiplatform approach, this has the downside of needing to rewrite the app
completely from scratch, reeducating our engineering team to add a new platform with new paradigms and ux to it, and in
the end was rejected because of these reasons.

#### Other Solutions Not Considered

Other solutions: We were also considering other alternatives, including less invasive versions as using Kotlin
Multiplatform only for the hardware communication layer, but ultimately decided on using the wrapping approach since we
seemed to have the right expertise onboard, and have explored similar options before.

Wallet Frontend and Hardware interactions
-----------------------------------------

After settling on an approach, let us explore the technical implementation of sad solution. The following chapters cover
the hardware details, libraries included and explored.

### Security Keys

Making users phishing resistant is one of our goals, especially while interacting with private data as coming from a
state issued id. Therefore, we opted to build a secure layer on top, in which we are leveraging passkeys for registering 
and logging users in and to verify the presentation of digital documents by signing their validity.

In order to use passkeys following the FIDO2 standard and some extensions, we needed to find a way to addresses this
hardware from the wwallet frontend website through the wrapper.

### Passkeys in Browsers

When the user accesses our website for registering her accounts, she can either use our custom [firefox browser
build](https://github.com/Yubico/e9g-tla-firefox-hg2git), or rely on the wrapper of the Android App to ensure her data
security, or consider a fallback trading security for convenience.

If she takes the most secure route for her data, she will have a security hardware key that implements the FIDO2 standard and
contains the PRF and SIGNING extensions. Once those conditions are met, she installs the app, registers, and the wallet
will ask her to present her security key. This is done by calling `naviagtor.credentials.create` which will create
secure credentials inside the browser.

This is where the interaction with the wrapper starts: After the wrapper is initialized, it overwrites the
`navibator.credentials.*` javascript API with it's own implementations.

This is done by injecting an Android Native bridge, showing up as `nativeWrapper` for the javascript website, but also
overwriting the aforementioned methods. Once the website calls those methods, the wrapper catches this call and
redirects the creation options. These options are then sent to the native android
SDK [credential manager](https://developer.android.com/identity/sign-in/credential-manager), which executes the request and responds with a
created credentials. Lastly the wrapped method will take the response from the SDK, and converts it into javascript
native objects in JSON.

For the wallet website being wrapped, it looks like a normal call to the `navigator.credentials.create` function, but in
the background something completely different is happening.

#### Promises

Of special note are JavaScripts `Promises`: the functionality to execute code at a later point in time. While a similar
concept also exists in Kotlin ([i.e. Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)), they are sadly
not one on one compatible, so the
wrapper also has to wrap those promises. This is achieved by creating an API for the JS side that uses
promises, but maps them to use callbacks to be able to call them from kotlin: Whenever a method is called that
returns a promise to JS, a new object containing the `reject` and `resolve` function from a new Promise is created.
Additionally, a new UUID is created as a reference to the Kotlin side for this Promise. A callback inside the native
bridge can take those promise uuids, execute the associated work, and respond back by finding the promise based on it's
uuid. Lastly the bridge calls the promise's reject or resolve functions, reporting back to JS.

### Signing and PRF extensions

Since we are augmenting the website with native communication, we can also add new functionality that is not currently
available on the Androids native web browser. For instance we can add the pseudo random function (PRF) FIDO2 extension
when creating a new token, and asserting it when loging in the user. This way we can enhance the users security by
reinforcing the use of hardware authentication.

Similar for the SIGN extension: Ths SIGN extension can be used to ask the authenticator (the security key) to take a
blob of data and sign it, so a receiver, maybe a verifier, knows who owns the data and can ensure temperproof
presentations.

#### State of PRF in Web

Luckily the extension mechanism of the FIDO2 standard are public and applications are able to implement them on their
own time. There are already a number of implementation of the FIDO2 / Webauthn that can make use of the PRF extension,
but sadly the number was not high enough to not exclude users without the needed software update.

#### Signing online

Whereas the PRF extension already has some implementations, the SIGN extension is currently only available for use in
this app, the iOS wallet proposal and our custom build of Firefox.


Presentment
-----------

Presentment is the process of presenting a document to a verifying party: Think of it as presenting your eID at a police
checkup, a bar tender verifying your age, or to show your university diploma to a potential new employer. The wwwwalet
already contains the presentment for documents online and readers are encouraged to read up on the online presentment
there. The following chapters are dedicated to explaining how the in person presentment works: How two phones can share
parts of documents securely while being close to each other.

### Bluetooth Low Energy

A brought number of mobile phones support the Low Energy profile for Bluetooth, so we chose this, together with the
ISO-18013-5 this forms the basis of this communications.

### WebBle and extensibility

ISO-18013-5 allows several way how two mobile device can communicate: The verifying app ('Verifier') as the Bluetooth LE
server of the communication is called `MDoc Reader` and as contrast the `MDoc` mode establishes the Wallet as the server
of the Bluetooth LE server. Independently of the mode the communication is established in the server and the client
communicates through `Charactersitics` and `Services` in Bluetooth LE.

### Existing solutions

In order to implement said communication, we elaborate existing solutions on the market, emphasizing opensource
solutions to support their development and for ease of integration.

Sadly especially for the Android Wrapper, the situation showed as more complicated as hoped: Either the exiting code
bases where not meeting basic requirements of code quality, where not extensible enough, or where to tightly integrated
into other existing systems.

Therefore, we decided to implement the ble communication from scratch and offer the wwwallet frontend a simple API for
interacting with ble: The idea being that the Android wrapper abstracts the ble communication, and lets the frontend
simply ask the wrapper to send binary data through it. This way the frontend doesn't need to know the integralities of BLE
communication, but needs to be able to convert the wwwwallet contents into a binary representation and communicate
securely with the verifier. It resolves into a clear separation between transport (the ble wrapper implementation) and
communication (the wwwwallet).

An overview of the API offered to the JS frontend is this:

| API     | Description                   | Parameter               | Return                                                     |
|---------|-------------------------------|-------------------------|------------------------------------------------------------|
| mode    | set the mode of communication | "mdoc reader" or "mdoc" | (immediate)                                                |
| create  | create a server / client      | uuid of ble service     | A promise that gets resolved when server/client is created |
| send    | send data to client / server  | string to be send       | A promise resolved when completed                          |
| receive | prepare to receive dataa      | nothing                 | A promise resolving with a string                          |
| state   | (debug) returns state info    | nothin                  | A string with mode, server / client states                 | 

#### Example

The following javascript sample, illustrates the usage of above's API description.

```js
let uuid = // uuid from verifiyer to connect to
    nativeWrapper.createClient(uuid)
        .then(nativeWrapper.sendToServer([1, 3, 4, 5]))
        .then(nativeWrapper.receiveFromServer())
        .then(result =>
            console.log(JSON.parse(result))
        )
```

This code sample creates a BLE client ('mdoc reader' mode) in the wrapper, sends random bytes to the server (the
reader / verifier) and "waits" for the server to send data back. Once the data is received, they get printed on the
console.

With that snippet we got the opportunity to implement the complete protocol on the wwallet, without needing to
understand BLE. This also offers us the opportunity to replace the wrapper BLE communications with Web Bluetooth at a
later point in time.

Offline
-------

Another crucial point of communicating between mobiles is the ability to communicate while being not connected to the
internet. Imagine a police control outside bigger cities without any LTE / 5G in the area, or an age check deep
inside a big bulky building, where no Wi-Fi or GSM signals can reach.

For those situations, the wrapper needs to offer a minimum of functionality so that the presentments can still happen.
For obvious reasons the offline presentment requires that the user has logged in once before so that her data is
availble to the wrapper and frontend.

### `Capacitor`

Inside the wrapper we already intercept JS calls to `navigator.credentials.create` and our own custom `nativeWrapper.*`,
and additionally it is easy to intercept calls to open / request assets and new websites. In order to make the wrapper
work offline, we have to intercept known url requests and deliver the known files. It is not dissimilar to hosting your
own localhost version of the wwwwallet on Android, but without the actual webserver hosting part.

While completely feasible to reimplement the file / url matching by hand, in this case we decided to use a tool
called [capacitor]() for time and complexity sake.

After making the source code of the wwwallet available as a git submodule
under [funke-wwallet-frontend](./funke-wallet-frontend) we can use a combination of these building steps to build the
website locally, expose it into the Android App, and inform the wrapper of the new frontend:

```shell
cd ./funke-wallet-frontend
npm install
yarn build
npm install capacitor
npx cap add android
npx cap update
```

The plan is to also integrate those steps into the build process of the wrapper using the gradle task
`updateWalletFrontend` so it could be automated, whenever the wrapper is build:

```shell
./gradlew updateWalletFrontend installDebug
```

### Alternatives

Sadly the alternatives like `bubblewrap` or similar did not offer much more then a thin abstraction layer on the WebView
without taking away the mapping from requested webpage to local files.

We also added minimal offline support to the wwwallet frontend js code itself, by using `Service Workers` and similar
JS functionality, readers are encouraged to read on the appropriate documentation overthrew.

Learnings
---------

Additionally, to the learnings described in the chapters before, some tools and libraries need special mentioning since
they allowed us to speed up development tremendously:

* [jitpack](https://jitpack.io) a tool to integrate in development libraries and modules for Android apps, without needing to
  host an own nexus or other in between releases maven dependency providers
* [scrcpy](https://github.com/Genymobile/scrcpy) for sharing a connected Android app screen to desktop

Next Steps
----------

Thanking you.

