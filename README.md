# gexporter
Android App to export GPX and FIT to garmin devices

* ConnectIQ App: https://apps.garmin.com/de-DE/apps/de11adc4-fdbb-40b5-86ac-7f93b47ea5bb
* Android App: https://play.google.com/store/apps/details?id=org.surfsite.gexporter

## HOWTO
* create directory app/libs
* copy fit.jar from the Garmin FitSDK to app/libs
* Android Studio -> Settings -> System Settings -> Android SDK -> "SDK Tools" Tab -> Check "Support Repository/Constraint Layout"
* compile and install the app on your device where the Garmin Connect app is also running
* start the app
* start the connect IQ app on your garmin device https://github.com/gimportexportdevs/gimporter

## TODO
* create proper GUI
* properly display what is exported
* make the server a background service
* configure the list of directories to scan
* configure port
* make use of connectiq android SDK
* receive intent for GPX and FIT files
