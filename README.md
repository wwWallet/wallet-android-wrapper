[![Android Build and Publish](https://github.com/wwWallet/wwwallet-android-wrapper/actions/workflows/release.yml/badge.svg)](https://github.com/wwWallet/wwwallet-android-wrapper/actions/workflows/release.yml)

wwWallet Android
================

An Android native application wrapping `https://demo.wwwallet.org`. It is intended as a research project for the
Funke and Sprintd competition.


Running
-------

You can install the Android app by either [Building](#building) it, or by exploring this repositories [releases](https://github.com/wwWallet/wwwallet-android-wrapper/releases/latest).


Building
--------

This project uses gradle as a build tool:

```shell
./gradlew installDebug
```

The above commands will build and install the Android application (apk) on all connected phones.



Wrapping
--------

This Android application "wraps" the https://demo.wwwallet.org/ website, providing direct interaction with Yubikeys and
an initial set of bluetooth communication with a verifier. The wrapping happens by loading the website inside an Android
native `WebView` and intercepting interesting js code calls and websites to be loaded.

### Wrapping JS in Kotlin / Android

The aforementioned WebView is used to load the website and intercept not only a locally build version of the website,
but also to catch and wrap incoming javascript calls dealing with Yubikeys and Bluetooth communication. This is done, so
the website can be augmented with native kotlin code directly interacting with hardware: On the one hand for
communicating with Yubikeys, but also for communicating with Bluetooth.

### Alternatives to wrapping

Wrapping the website were not the only options considered. Sadly the alternatives were rejected in favor of
wrapping, as explored in the upcoming sections.

#### Kotlin Multiplatform

Kotlin, the language promoted by Google and Jetbrains to write Android Apps, can also be used to write applications
that are running on several platforms. At the time of this writing, those platforms are iOS, Android, Web and iOT
devices.

Exploring this option to run our wallet on Android revealed, that the interoperability between React Native, the
Javascript framework used to build the website, cannot be easily replicated inside Kotlin Multiplatform. To use this
alternative, we would need to either port our existing website or start from scratch. While that would open up the
possibility to support more platforms (i.e. Desktops), the effort of translating and reeducation needed for that
development was deemed to high.

#### Native

Where Kotlin Multiplatform is not an option we chose, what about building a native app for Android? This option has the
opportunity to use platform paradigms to confuse the user less about potential webisms introduced by wrapping a website
inside a native app.

But similar to the potential Kotlin Multiplatform approach, this has the downside of needing to rewrite the app
completely from scratch, reeducating our engineering team to add a new platform with new paradigms and UX to it, and in
the end was rejected because of these reasons.

#### Other Solutions Not Considered

Other solutions: We were also considering other alternatives, including less invasive versions as using Kotlin
Multiplatform only for the hardware communication layer, but ultimately decided on using the wrapping approach since we
seemed to have the right expertise onboard, and have explored similar options before.


Wallet Frontend and Hardware interactions
-----------------------------------------

After settling on an approach, let us explore the technical implementation of said solution. The following chapters cover
the hardware details, libraries included and explored.

### Security Keys

Making users phishing resistant is one of our goals, especially while interacting with private data as coming from a
state issued ID. Therefore, we opted to build a secure layer on top, in which we are leveraging device bound passkeys
from Yubico for registering and logging users in and to verify the presentation of digital documents by signing their
validity.

In order to use Passkeys following the FIDO2 standard and some extensions, we needed to find a way to address this
hardware from the wallet frontend website through the wrapper.

### Passkeys in Browsers

When the user accesses our website for registering her accounts, she can either use our custom [firefox browser
build](https://github.com/Yubico/e9g-tla-firefox-hg2git), or rely on the wrapper of the Android App to ensure her data
security, or consider a fallback trading security for convenience.

If she takes the most secure route for her data, she will have a security token that implements the FIDO2 standard and
contains the PRF and SIGNING extensions. Once those conditions are met, she installs the app, registers, and the wallet
will ask her to present her security key. This is done by calling `naviagtor.credentials.create` which will create
secure credentials inside the browser.

This is where the interaction with the wrapper starts: After the wrapper is initialized, it overwrites the
`navigator.credentials.*` javascript API with it's implementations.

This is done by injecting an Android Native bridge, showing up as `nativeWrapper` for the javascript website, but also
overwriting the aforementioned methods. Once the website calls those methods, the wrapper catches this call and
redirects the creation options. These options are then sent to the native android
SDK [yubikit-android](https://github.com/Yubico/yubikit-android), which executes the request and ideally responds with the
created credentials. Lastly the wrapped method will take the response from the SDK, and convert it into javascript
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
bridge can take those promise UUIDs, execute the associated work, and respond back by finding the promise based on its
UUID. Lastly the bridge calls the promise's reject or resolve functions, reporting back to JS.

### Signing and PRF extensions

Since we are augmenting the website with native communication, we can also add new functionality that is not currently
available on the Android native web browser. For instance we can add the pseudo random function (PRF) FIDO2 extension
when creating a new token, and asserting it when logging in the user. This way we can enhance the users security by
reinforcing the use of hardware authentication.

Similar for the SIGN extension: Ths SIGN extension can be used to ask the authenticator (the security key) to take a
blob of data and sign it, so a receiver, maybe a verifier, knows who owns the data and can ensure tamperproof
presentations.

#### State of PRF in Web

Luckily the extension mechanism of the FIDO2 standard are public and applications are able to implement them on their
own terms. There are already a number of implementations of FIDO2 / Webauthn that can make use of the PRF extension,
but sadly the number was not high enough to not exclude users without the needed software update.

#### Signing online

Whereas the PRF extension already has some implementations, the SIGN extension is currently only available for use in
this app, the iOS wallet proposal and our custom build of Firefox.

### Yubico's SDK

The Yubico SDK for Android [yubkit-android]((https://github.com/Yubico/yubikit-android) is used heavily for
communicating, signing and verifying strong security when presenting documents or registering and signing into the
wallet frontend.
See [NavigatorCredentialsContainerYubico](wrapper/src/main/java/org/siros/wwwallet/credentials/YubicoContainer.kt),
how the sdk is integrated.

Alternatives for using Yubico's SDK was to either use the Android Platform FIDO2/ Webauthn implementation, but sadly
this SDK was missing the required signing extension and for convenience the NFC functionality to create a credential
with a PIN.

Presentment
-----------

Presentment is the process of presenting a document to a verifying party: Think of it as presenting your eID at a police
checkup, a bar tender verifying your age, or to show your university diploma to a potential new employer. The wallet
already contains the presentment for documents online and readers are encouraged to read up on the online presentment
there. The following chapters are dedicated to explaining how the in-person presentment works: How two phones can share
parts of documents securely while being close to each other.

### Bluetooth Low Energy

A huge number of mobile phones support the Low Energy profile for Bluetooth, so we chose this, together with the
ISO-18013-5 standard, this forms the basis of the communication.

### WebBle and extensibility

ISO-18013-5 allows several ways how two mobile device can communicate: The verifying app ('Verifier') as the Bluetooth LE
server of the communication is called `MDoc Reader` and as contrast the `MDoc` mode establishes the Wallet as the server
of the Bluetooth LE server. Independently of the mode, the communication is established at the server and the client
communicates through `Characteristics` and `Services` in Bluetooth LE.

### Existing solutions

In order to implement said communication, we elaborate existing solutions on the market, emphasizing open source
solutions to support their development and for ease of integration.

Sadly especially for the Android Wrapper, the situation showed more complicated than hoped: Either the exiting code
bases were not meeting basic requirements of code quality, were not extensible enough, or were to tightly integrated
into other existing systems.

Therefore, we decided to implement the BLE communication from scratch and offer the wwwallet frontend a simple API for
interacting with BLE: The idea being that the Android wrapper abstracts the BLE communication, and lets the frontend
simply ask the wrapper to send binary data through it. This way the frontend doesn't need to know the internalities of the BLE
communication, but needs to be able to convert the wwwallet contents into a binary representation and communicate
securely with the verifier. It resolves into a clear separation between transport (the BLE wrapper implementation) and
communication (the wwwallet).

An overview of the API offered to the JS frontend is this:

| API     | Description                   | Parameter               | Return                                                     |
|---------|-------------------------------|-------------------------|------------------------------------------------------------|
| mode    | set the mode of communication | "mdoc reader" or "mdoc" | (immediate)                                                |
| create  | create a server / client      | UUID of BLE service     | A promise that gets resolved when server/client is created |
| send    | send data to client / server  | string to be sent       | A promise resolved when completed                          |
| receive | prepare to receive data       | nothing                 | A promise resolving with a string                          |
| state   | (debug) returns state info    | nothing                 | A string with mode, server / client states                 | 

#### Example

The following javascript sample illustrates the usage of above's API description.

```js
let uuid = // UUID from verifiyer to connect to
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

With that snippet we got the opportunity to implement the complete protocol in wwallet, without needing to
understand BLE. This also offers us the opportunity to replace the wrapper BLE communications with Web Bluetooth at a
later point in time.

Offline
-------

Another crucial point of communicating between mobile phones is the ability to communicate while not being connected to the
Internet. Imagine a police control outside bigger cities without any LTE / 5G in the area, or an age check deep
inside a big bulky building, where no Wi-Fi or GSM signals can reach.

For those situations, the wrapper needs to offer a minimum of functionality so that the presentments can still happen.
For obvious reasons, the offline presentment requires that the user has logged in once before so that her data is
available to the wrapper and frontend.

Inside the wrapper we already intercept JS calls to `navigator.credentials.create` and our own custom `nativeWrapper.*`,
and additionally it is easy to intercept calls to open / request assets and new websites. In order to make the wrapper
work offline, we have to intercept known url requests and deliver the known files. It is not dissimilar to hosting your
own localhost version of the wwwallet on Android, but without the actual webserver hosting part.


### Alternatives

Sadly the alternatives like `bubblewrap` or similar did not offer much more then a thin abstraction layer on the WebView
without taking away the mapping from requested webpage to local files.

We also added minimal offline support to the wwwallet frontend JS code itself, by using `Service Workers` and similar
JS functionality, readers are encouraged to read on the appropriate documentation over there.

Learnings
---------

Additionally, to the learnings described in the chapters before, some tools and libraries need special mentioning since
they allowed us to speed up development tremendously:

Next Steps
----------

Thanking you.

