# Repro for Akka HTTP Multipart Upload

## Setup

* Open 2 terminals.
* Run the server on the first terminal: `sbt "run-main test.Server"`
* Run the client on the second terminal: `sbt "run-main test.Client"`

## Observation

### Client

The client will upload 2 files in a single multipart HTTP post.
* The first body part is `src/main/resources/bundle.conf`
* Next body part is `src/main/resources/dummy-bundle-3e1f22ec0b81843e6273c300f0ba3958e0cdc6f65da58e02e1c9dddd3d3cab29.zip`.

### Server

The server will attempt the following.

* First `Unmarshal(request.entity).to[FormData]`.
* Extract the first 2 body part by doing `formData.parts.prefixAndTail(2)`.
* The `prefixAndTail(2)` produces `(Seq[Multipart.FormData.BodyPart], Source[Multipart.FormData.BodyPart, _])`.
* The `Seq[Multipart.FormData.BodyPart]` will have 2 elements.
* The first element of the list is the upload from `src/main/resources/bundle.conf`. This is streamed into a file sink.
* The second element of the list is prepended to the remaining `Source[Multipart.FormData.BodyPart, _]`. This is streamed into `Sink.ignore`.

### What should happen

The expected result is the content from the uploaded `src/main/resources/bundle.conf` is successfully written into a file.

### What actually happens

* There's a problem materializing the stream which writes the uploaded `src/main/resources/bundle.conf` into a file sink. The following error is displayed.

```
[ERROR] [09/29/2016 22:08:54.524] [test-server-akka.stream.default-blocking-io-dispatcher-26] [akka://test-server/user/StreamSupervisor-0/flow-29-1-fileSink] Tearing down FileSink(/var/folders/ls/f4kkzrhd2tl80ztkmg93j92r0000gn/T/test-server4688945237626226189/bundle.conf) due to upstream error
akka.stream.impl.SubscriptionTimeoutException: Substream Source has not been materialized in 5000 milliseconds
	at akka.stream.impl.fusing.SubSource.timeout(StreamOfStreams.scala:692)
	at akka.stream.stage.GraphStageLogic$SubSourceOutlet.timeout(GraphStage.scala:1062)
	at akka.stream.impl.fusing.PrefixAndTail$PrefixAndTailLogic.onTimer(StreamOfStreams.scala:129)
	at akka.stream.stage.TimerGraphStageLogic.akka$stream$stage$TimerGraphStageLogic$$onInternalTimer(GraphStage.scala:1152)
	at akka.stream.stage.TimerGraphStageLogic$$anonfun$akka$stream$stage$TimerGraphStageLogic$$getTimerAsyncCallback$1.apply(GraphStage.scala:1141)
	at akka.stream.stage.TimerGraphStageLogic$$anonfun$akka$stream$stage$TimerGraphStageLogic$$getTimerAsyncCallback$1.apply(GraphStage.scala:1141)
	at akka.stream.impl.fusing.GraphInterpreter.runAsyncInput(GraphInterpreter.scala:691)
	at akka.stream.impl.fusing.GraphInterpreterShell.receive(ActorGraphInterpreter.scala:419)
	at akka.stream.impl.fusing.ActorGraphInterpreter.akka$stream$impl$fusing$ActorGraphInterpreter$$processEvent(ActorGraphInterpreter.scala:603)
	at akka.stream.impl.fusing.ActorGraphInterpreter$$anonfun$receive$1.applyOrElse(ActorGraphInterpreter.scala:618)
	at akka.actor.Actor$class.aroundReceive(Actor.scala:484)
	at akka.stream.impl.fusing.ActorGraphInterpreter.aroundReceive(ActorGraphInterpreter.scala:529)
	at akka.actor.ActorCell.receiveMessage(ActorCell.scala:526)
	at akka.actor.ActorCell.invoke(ActorCell.scala:495)
	at akka.dispatch.Mailbox.processMailbox(Mailbox.scala:257)
	at akka.dispatch.Mailbox.run(Mailbox.scala:224)
	at akka.dispatch.Mailbox.exec(Mailbox.scala:234)
	at scala.concurrent.forkjoin.ForkJoinTask.doExec(ForkJoinTask.java:260)
	at scala.concurrent.forkjoin.ForkJoinPool$WorkQueue.runTask(ForkJoinPool.java:1339)
	at scala.concurrent.forkjoin.ForkJoinPool.runWorker(ForkJoinPool.java:1979)
	at scala.concurrent.forkjoin.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:107)
```

* Despite the error, writing to the file is completed resulting in an empty file.

## Comparison with curl

Upload using `curl` is completed successfully.

Open another terminal and run `sh upload-with-curl.sh`.

This will upload `src/main/resources/bundle.conf` and `src/main/resources/dummy-bundle-3e1f22ec0b81843e6273c300f0ba3958e0cdc6f65da58e02e1c9dddd3d3cab29.zip` using `curl`.

The server will print the content of the uploaded `src/main/resources/bundle.conf` into the `stdout`. The client should get HTTP code `200` as the response code and the response body is a plain text of the path of the saved file on the server.`
