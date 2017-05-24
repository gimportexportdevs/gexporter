# gexporter
Android App to export GPX and FIT to garmin devices

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
