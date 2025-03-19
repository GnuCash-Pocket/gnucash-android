[![CI Build](https://github.com/GnuCash-Pocket/gnucash-android/actions/workflows/ci-build.yml/badge.svg)](https://github.com/GnuCash-Pocket/gnucash-android/actions/workflows/ci-build.yml)
[![Nightly Build](https://github.com/GnuCash-Pocket/gnucash-android/actions/workflows/nightly-build.yml/badge.svg)](https://github.com/GnuCash-Pocket/gnucash-android/actions/workflows/nightly-build-test.yml)

# Introduction

GnuCash Pocket is a companion expense-tracker application for GnuCash (desktop) designed for Android. It allows you to record transactions on-the-go and later import the data into GnuCash for the desktop.

This app is a continuation of [codinguser's "Gnucash for Android"](https://github.com/codinguser/gnucash-android), which is already abandoned.

Accounts            |  Transactions          |  Reports
:-------------------------:|:-------------------------:|:-------------------------:
![Accounts List](docs/images/v2.0.0_home.png)  |  ![Transactions List](docs/images/v2.0.0_transactions_list.png) |  ![Reports](docs/images/v2.0.0_reports.png)

The application supports Android 5 Lollipop (API level 21) and above.

Features include:
  * An easy-to-use interface.
  * **Chart of Accounts**: A master account can have a hierarchy of detail accounts underneath it. This allows similar account types (e.g. Cash, Bank, Stock) to be grouped into one master account (e.g. Assets).
  * **Split Transactions**: A single transaction can be split into several pieces to record taxes, fees, and other compound entries.
  * **Double Entry**: Every transaction must debit one account and credit another by an equal amount. This ensures that the "books balance": that the difference between income and outflow exactly equals the sum of all assets, be they bank, cash, stock or other.
  * **Income/Expense Account Types (Categories)**: These serve not only to categorize your cash flow, but when used properly with the double-entry feature, these can provide an accurate Profit&Loss statement.
  * **Scheduled Transactions**: GnuCash has the ability to automatically create and enter transactions.
  * **Export to GnuCash XML**, QIF or OFX. Also, scheduled exports to 3rd-party sync services like DropBox and Google Drive
  * **Reports**: View summary of transactions (income and expenses) as pie/bar/line charts

# Installation

There are different ways to get the GnuCash Pocket app for Android – through the app store, from GitHub or by building it yourself.

### App Store

<a href="http://play.google.com/store/apps/details?id=org.gnucash.pocket.pnemonic">
  <img alt="Android app on Google Play" src="http://developer.android.com/images/brand/en_generic_rgb_wo_60.png" />
</a>

### From GitHub

Download the .apk from [Releases](https://github.com/GnuCash-Pocket/gnucash-android/releases). Alternatively you can check latest [CI Build action](https://github.com/GnuCash-Pocket/gnucash-android/actions/workflows/ci-build.yml).

## Building

### With Gradle

This project requires the [Android SDK](http://developer.android.com/sdk/index.html) to be installed in your development environment. In addition you'll need to set the `ANDROID_HOME` environment variable to the location of your SDK. For example:

    export ANDROID_HOME=/home/<user>/tools/android-sdk

After satisfying those requirements, the build is pretty simple:

* Run `./gradlew build installDevelopmentDebug` from the within the project folder. It will build the project for you and install it to the connected Android device or running emulator.

The app is configured to allow you to install a development and production version in parallel on your device.

### With Android Studio

The easiest way to build is to install [Android Studio](https://developer.android.com/sdk/index.html) v2.+ with [Gradle](https://www.gradle.org/) v3.4.1. Once installed, then you can import the project into Android Studio:

1. Open `File`
2. Import Project
3. Select `build.gradle` under the project directory
4. Click `OK`

Then, Gradle will do everything for you.

However, this project uses Firebase and therefore relies on a `google-services.json` configuration file. This file is not included in this repo and every contributor is encouraged to generate its own.

When importing the project in Android Studio the build task will fail with the following error:

`org.gradle.api.GradleException: File google-services.json is missing. The Google Services Plugin cannot function without it.`

or something similar depending on the Android Studio version you're using.

In order to generate a `google-services.json` configuration file follow these steps (Note: it requires a Google account):

- Open the [Firebase Console](https://console.firebase.google.com/).
- Login with your Google account.
- Create a new project (name doesn't matter).
- Select "_Add Firebase to your Android app_".
- Provide package name:  `org.gnucash.android`.
- Register app.
- Download `google-services.json` file.
- Follow instructions to add file to project.
- Skip "_Add Firebase SDK step_".
- Run app to verify that the configuration is picked up correctly.

You can also follow [these instructions](https://firebase.google.com/docs/android/setup).

## Support

GitHub [Issues](https://github.com/GnuCash-Pocket/gnucash-android/issues)

## Contributing

There are several ways you could contribute to the development.

* Pull requests are always welcome! You could contribute code by fixing bugs, adding new features or automated tests. Take a look at the [bug tracker](https://github.com/GnuCash-Pocket/gnucash-android/issues?state=open) for ideas where to start. It is also preferable to target issues in the current [milestone](https://github.com/GnuCash-Pocket/gnucash-android/milestones). 
* Make sure to read our [contribution guidelines](https://github.com/GnuCash-Pocket/gnucash-android/blob/master/.github/CONTRIBUTING.md) before starting to code. 

* Another way to contribute is by providing translations for languages, or improving translations. The original app by [codinguser](https://github.com/codinguser/) was translated on [CrowdIn](https://crowdin.com/project/gnucash-android)

For development, it is recommended to use the Android Studio for development which is available for free. Import the project into the IDE using the build.gradle file. The IDE will resolve dependencies automatically.

# License

GnuCash Pocket is free software; you can redistribute it and/or modify it under the terms of the Apache license, version 2.0. You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
