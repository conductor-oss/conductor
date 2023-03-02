<div class="container hero">
  <div class="row justify-content-center align-items-center">
    <div class="col-6">
      <div class="heading">
        Scalable Workflow Orchestration
      </div>
      <div class="caption pt-3">
        Conductor is a platform created by <b>Netflix</b> to orchestrate workflows that span across microservices.
      </div>
      <div class="mt-5"> 
      <a type="button" class="btn btn-primary" href="/gettingstarted/local.html">Get Started</a>
      </div>
    </div>
    <div class="col-6">
      <img src="/img/workflow.svg" class="illustration">
    </div>
  </div>
</div>


<div class="container bullets">
  <div class="row justify-content-center">
    <div class="col-4">
      <div class="heading">
        <img src="/img/icons/osi.svg" class="icon"/> Open Source
      </div>
      <div class="caption">
        Apache-2.0 license for commercial and non-commerical use. Freedom to deploy, modify and contribute back.
      </div>
    </div>
    <div class="col-4">
      <div class="heading">
        <img src="/img/icons/modular.svg" class="icon"/> Modular
      </div>
      <div class="caption">
        A fully abstracted backend enables you choose your own database persistence layer and queueing service.
      </div>
    </div>
    <div class="col-4">
      <div class="heading">
        <img src="/img/icons/shield.svg" class="icon"/> Proven
      </div>
      <div class="caption">        
        Enterprise ready, Java Spring based platform that has been battle tested in production systems at Netflix and elsewhere.
      </div>
    </div>
  </div>
  
  
  <div class="row justify-content-center">
    <div class="col-4">
      <div class="heading">
         <img src="/img/icons/wrench.svg" class="icon"/> Control
      </div>
      <div class="caption">        
        Powerful flow control constructs including Decisions, Dynamic Fork-Joins and Subworkflows. Variables and templates are supported.
      </div>
    </div>
    <div class="col-4">
      <div class="heading">
        <img src="/img/icons/brackets.svg" class="icon"/> Polyglot
      </div>
      <div class="caption">        
        Client libraries in multiple languages allows workers to be implemented in Java, Node JS, Python and C#.
      </div>
    </div>
    <div class="col-4">
      <div class="heading">
         <img src="/img/icons/server.svg" class="icon" /> Scalable
      </div>
      <div class="caption">        
        Distributed architecture for both orchestrator and workers scalable from a single workflow to millions of concurrent processes.
      </div>
    </div>
  </div>
</div>

<div class="container module">
  <div class="row align-items-center">
    <div class="col-6">
      <div class="heading">
        Developer Experience
      </div>
      <div class="caption">        
        <ul>
          <li>Discover and visualize the process flows from the bundled UI</li>
          <li>Integrated interface to create, refine and validate workflows</li>          
          <li>JSON based workflow definition DSL</li>
          <li>Full featured API for custom automation</li>
        </ui>
      </div>
    </div>
    <div class="col-6">
      <div class="screenshot" style="background-image: url(/img/tutorial/Switch_UPS.png);"></div>
    </div>
  </div>
</div>

<div class="container module">
  <div class="row">
    <div class="col-6">
      <div class="heading">
        Observability
      </div>
      <div class="caption">    
        <ul>
          <li>Understand, debug and iterate on task and workflow executions.</li>
          <li>Fine grain operational control over workflows with the ability to pause, resume, restart, retry and terminate</li>
        </ul>
      </div>
    </div>
    <div class="col-6">
      <div class="screenshot" style="background-image: url(/img/timeline.png);"></div>
    </div>
  </div>
</div>


<div class="compare">
  <div class="container">
    <div class="row">
      <div class="col-12">
        <h2 class="heading">Why Conductor?</h2>
      </div>
    </div>
    <div class="row align-items-stretch">
      <div class="col-6">
      <div class="bubble">
        <h3 class="heading">
           <img src="/img/favicon.svg" class="icon"/> Service Orchestration
        </h3>
        <div class="caption">        
          <p>Workflow definitions are decoupled from task implementations. This allows the creation of process flows in which each individual task can be implemented 
          by an encapsulated microservice.</p>
          <p>Designing a workflow orchestrator that is resilient and horizontally scalable is not a simple problem. At Netflix we have developed a solution in <b>Conductor</b>.</p>
        </div>
        </div>
      </div>
      <div class="col-6">
      <div class="bubble">
        <h3 class="heading">
          <img src="/img/icons/network.svg" class="icon"/> Service Choreography
        </h3>
        <div class="caption">        
          Process flows are implicitly defined across multiple service implementations, often with
          tight peer-to-peer coupling between services. Multiple event buses and complex
          pub/sub models limit observability around process progress and capacity. 
        </div>
      </div>
      </div>
    </div>
  </div>
</div>
