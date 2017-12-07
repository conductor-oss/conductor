## Usage

1. In `server/build.gradle` file,  add `compile project(':conductor-es5-persistence')` in dependencies
1. In `server/ConductorServer.java` file , replace  `import com.netflix.conductor.dao.es.EmbeddedElasticSearch` with `import com.netflix.conductor.dao.es5.es.EmbeddedElasticSearch`
1. In `server/ServerModule.java` file,  replace  `import com.netflix.conductor.dao.index.ElasticSearchDAO; import com.netflix.conductor.dao.index.ElasticsearchModule` with `import com.netflix.conductor.dao.es5.index.ElasticSearchDAO;  import com.netflix.conductor.dao.es5.index.ElasticsearchModule;`
1. Config property 'workflow.elasticsearch.cluster.name' , value with your elasticsearch cluster name
