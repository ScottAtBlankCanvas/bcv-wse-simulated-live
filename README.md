# WSE Stream Demo Pusher

## Introduction

Allow publishing a VOD asset as a simulated live stream, triggered by an API call.

## Background

Wowza Streaming Engine has a great feature called Stream Demo Publisher that publishes a VOD as a live stream when WSE starts.  If you need a simulated live stream that starts and stops with WSE, I suggest you use this.

However, if you need to control the starting and stopping of the stream, this module will solve that



## Configuration

- After installing WSE, copy build/bcv-wse-simulated-live.jar into <WSE_INSTALL>/lib
- You have already created an application to receive the simulated live stream.  (Let's assume its called "ingest")
- The module is an HTTP Provider, which means you must add to the HTTP Provider to one of the VHost ports.  (ex. 1935)

```
<HTTPProvider>
	<BaseClass>com.blankcanvas.video.simulated-live.HTTPProviderStreamPusher</BaseClass>
	<RequestFilters>bcv-sim-live*</RequestFilters>
	<AuthenticationMethod>none</AuthenticationMethod>
</HTTPProvider>

```

- Copy the VOD you wish to stream as a simulated live into the context folder (usually <WSE_INSTALL>/content)
- Restart WSE

## Usage


To create a stream called myStream in the application ingest using a VOD called myVOD.mp4:

```
 curl <WSE_IP>:1935/bcv-sim-live\?file=myVOD.mp4\&app=ingest\&name=myStream\&action=start
```
 

To stop it:

```
 curl <WSE_IP>:1935/bcv-sim-live\?app=ingest\&name=myStream\&action=stop
```

Stopping all simulated live streams:

```
 curl <IP>:1935/bcv-sim-live\?action=stop_all
```

Listing all started streams

```
 curl <WSE_IP>:1935/bcv-sim-live\?action=list
```

 

## Building

A simple ant script is provided:

```
ant jar ; to build the jar from source

ant clean  ; to remove class files and jar file
```

## Developing

The module is best developed using Eclipse.  

Wowza provides instructions for building modules inside of Eclipse.  Follow these and then import the project into Eclipse

The module uses public API calls, as documented with WSE







