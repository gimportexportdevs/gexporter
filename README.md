# gexporter
Android App to export GPX and FIT to garmin devices

* ConnectIQ App: https://apps.garmin.com/en-US/apps/de11adc4-fdbb-40b5-86ac-7f93b47ea5bb
* ConnectIQ Widget: https://apps.garmin.com/en-US/apps/fac50ef3-77b2-466c-9f4f-4dcb2feb49a3
* Android App: https://play.google.com/store/apps/details?id=org.surfsite.gexporter

## HELP
* see Wiki: https://github.com/gimportexportdevs/gexporter/wiki/Help

## HOWTO Develop
* create directory app/libs
* copy fit.jar from the Garmin FitSDK to app/libs
* Android Studio -> Settings -> System Settings -> Android SDK -> "SDK Tools" Tab -> Check "Support Repository/Constraint Layout"
* start TestServer.main() (this fires up the webserver on localhost)
* develop with the ConnectIQ simulator (connects to the webserver on localhost)

## TODO
* make use of connectiq android SDK
* create proper GUI
* make parameter preferences
* properly display what is exported
* configure the list of directories to scan
* configure port
