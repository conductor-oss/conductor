# Azure Blob External Storage Module 

This module use azure blob to store and retrieve workflows/tasks input/output payload that
went over the thresholds defined in properties named `conductor.[workflow|task].[input|output].payload.threshold.kb`.

**Warning** Azure Java SDK use libs already present inside `conductor` like `jackson` and `netty`.
You may encounter deprecated issues, or conflicts and need to adapt the code if the module is not maintained along with `conductor`.
It has only been tested with **v12.2.0**.

## Build

### Gradle Configuration

Modify the following files

[versionsOfDependencies.gradle](https://github.com/Netflix/conductor/blob/master/versionsOfDependencies.gradle)

```diff
@@ -5,6 +5,7 @@ ext {
     revApacheCommons='2.6'
     revAwaitility = '3.1.2'
     revAwsSdk = '1.11.86'
+    revAzureStorageBlobSdk = '12.2.0'
     revArchaius = '0.7.5'
     revCassandra = '3.6.0'
     revCassandraUnit = '3.5.0.1'


```

[settings.gradle](https://github.com/Netflix/conductor/blob/master/settings.gradle)

```diff
@@ -3,6 +3,7 @@ rootProject.name='conductor'
 include 'client','common','contribs','core', 'es5-persistence','jersey', 'postgres-persistence', 'zookeeper-lock', 'redis-lock'
 include 'cassandra-persistence', 'mysql-persistence', 'redis-persistence','server','test-harness','ui'
 include 'grpc', 'grpc-server', 'grpc-client'
+include 'azureblob-storage'

 rootProject.children.each {it.name="conductor-${it.name}"}

```

[server/build.gradle](https://github.com/Netflix/conductor/blob/master/server/build.gradle)

```diff
@@ -23,6 +23,7 @@ dependencies {

     //Conductor
     compile project(':conductor-core')
+    compile project(':conductor-azureblob-storage')
     compile project(':conductor-jersey')
     compile project(':conductor-redis-persistence')
     compile project(':conductor-mysql-persistence')

```
 
Delete all dependencies.lock files of all modules and then run at the root folder of the project: 

```
./gradlew generateLock saveLock -PdependencyLock.includeTransitives=true
```

Run Build along with the tests

```
./gradlew conductor:azureblob-storage build
```

## Configuration

In the `.properties` file of conductor `server` you must add the following configurations.

* Add the AzureBlob module. If you have several modules, separate them with a comma.
```
conductor.additional.modules=com.netflix.conductor.azureblob.AzureBlobModule
```

### Usage

Cf. Documentation [External Payload Storage](https://netflix.github.io/conductor/externalpayloadstorage/#azure-blob-storage)

### Example

```properties
conductor.additional.modules=com.netflix.conductor.azureblob.AzureBlobModule
es.set.netty.runtime.available.processors=false

workflow.external.payload.storage=AZURE_BLOB
workflow.external.payload.storage.azure_blob.connection_string=DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;EndpointSuffix=localhost
workflow.external.payload.storage.azure_blob.signedurlexpirationseconds=360
```

## Testing

You can use [Azurite](https://github.com/Azure/Azurite) to simulate an Azure Storage.

### Troubleshoots

* When using **es5 persistance** you will receive an `java.lang.IllegalStateException` because the Netty lib will call `setAvailableProcessors` two times. To resolve this issue you need to set the following system property

```
es.set.netty.runtime.available.processors=false
```

If you want to change the default HTTP client of azure sdk, you can use `okhttp` instead of `netty`.
For that you need to add the following [dependency](https://github.com/Azure/azure-sdk-for-java/tree/master/sdk/storage/azure-storage-blob#default-http-client).

```
com.azure:azure-core-http-okhttp:${compatible version}
```
