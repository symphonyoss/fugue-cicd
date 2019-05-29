package org.symphonyoss.s2.fugue.cicd.v3

import org.apache.commons.lang3.text.StrSubstitutor;
import org.jenkinsci.plugins.workflow.cps.DSL
import org.jenkinsci.plugins.workflow.cps.EnvActionImpl

import java.util.Map.Entry
import java.util.Random

class FuguePipeline extends JenkinsTask implements Serializable
{
  private static final String FUGUE_VERSION = 'v3'
  
  private EnvActionImpl environ
  private DSL           steps
  
  private Map podMap = [:]

  private Map aws_identity = [:]
  private Map docker_repo = [:]
  private Map<String, EnvironmentTypeConfig> environmentTypeConfig = [:]
  private Map<String, Container> service_map = [:]
  private Map<String, Station> stationMap = [:]
  private Map<String, String> role_map = [:]
  private Set<String> logGroupSet = []
  private String symbase = 'symbase'

  private globalNamePrefix_ = 'sym-s2-'
  private String fugueLogGroupName_ = globalNamePrefix_ + "fugue"
  private String serviceId_

  private Station deployStation
  private String awsRegion = 'us-east-1'
  private String release
  private String buildQualifier = new Date().format('yyyyMMdd-HHmmss-') + new Random().nextInt(9999)
  private String buildId = buildQualifier
  private String servicePath
  private boolean toolsDeploy = false
  private boolean useRootCredentials = false
  private boolean fugueDryRun_ = false
  private String  fugueAction_ = "Deploy"
  
  private boolean               doBuild_
  private Map<String, Boolean>  pushTo_ = [:]
  private Map<String, Boolean>  verifyCreds_ = [:]
  private Map<String, Purpose>  deployTo_ = [:]
  private String                pullFrom_
  private String                targetEnvironmentType_
  
  private String serviceGitOrg
  private String serviceGitRepo
  private String serviceGitBranch
  private String configGitOrg
  private String configGitRepo
  private String configGitBranch
  
  private String releaseTrack
  
  private FuguePipeline(EnvActionImpl env, DSL steps)
  {
    super(env, steps)
    
    this.environ = env
    this.steps = steps
  }

  public static FuguePipeline instance(EnvActionImpl env, DSL steps)
  {
    return new FuguePipeline(env, steps)
  }

  public EnvironmentTypeConfig  getEnvironmentTypeConfig(String environmentType)
  {
    return environmentTypeConfig[environmentType];
  }
  
  public String toString() {
    StringBuilder b = new StringBuilder();
    
    b.append "{" +
      "\n  release              =" + release +
      "\n  buildId              =" + buildId +
      "\n  servicePath          =" + servicePath +
      "\n  awsRegion            =" + awsRegion +
      "\n  roles                = [" + role_map + "]" +
      "\n  containers           = [" +
      "\n"
      
      boolean start = true
      
      for(Container ms : service_map.values())
      {
        if(start)
        {
          start = false;
        }
        else
        {
          b.append(",\n")
        }
        b.append ms.toString()
      }
      
      b.append "\n  ]\n}"
      
      return b.toString()
  }
  
  public FuguePipeline withContainer(Container s) {
    if(service_map.containsKey(s.name))
      throw new IllegalArgumentException("Container \"" + s.name + "\" redefined.")
    
    service_map.put(s.name, s)
    
    return this
  }
  
  public FuguePipeline withServiceName(String n) {
      this.serviceId_ = n
      return this
  }
  
  public FuguePipeline withPath(String n) {
    this.servicePath = n
    return this
  }
  
  public FuguePipeline withStation(Station station) {
    // It should be an error to include the same podName in the same environment twice in one station or
    // in separate stations
    stationMap.put(station.name, station)
    return this
  }
  
  public FuguePipeline withRelease(String v) {
      release = v
      buildId = release + "-" + buildQualifier
      return this
  }
  
  public FuguePipeline withBuildId(String v) {
      buildId = v
      return this
  }
  
  public FuguePipeline withRole(String name, String file) {
    if(role_map.containsKey(name))
      throw new IllegalArgumentException('Role "' + name + '" redefined.')
      
      role_map.put(name, file)
      
      return this
  }
  
  public FuguePipeline withServiceGitRepo(String repo) {
    serviceGitRepo = repo
    return this
  }
  
  public FuguePipeline withConfigGitRepo(String repo) {
    configGitRepo = repo
    return this
  }
  
  @Deprecated
  public FuguePipeline withServiceGitRepo(String org, String repo, String branch = 'master') {
    serviceGitOrg = org
    serviceGitRepo = repo
    serviceGitBranch = branch
    return this
  }
  
  @Deprecated
  public FuguePipeline withConfigGitRepo(String org, String repo, String branch = 'master') {
    configGitOrg = org
    configGitRepo = repo
    configGitBranch = branch
    return this
  }
  
  public FuguePipeline withReleaseTrack(String track) {
    releaseTrack = track
    return this
  }
  
  /** Intended for fugue-tools use only NOT TO BE CALLED BY NORMAL SERVICES */
  public FuguePipeline withToolsDeploy(boolean b) {
    toolsDeploy = b
    
    return this
  }
  
  /** Intended for fugue-tools use only NOT TO BE CALLED BY NORMAL SERVICES */
  public FuguePipeline withUseRootCredentials(boolean b) {
    useRootCredentials = b
    return this
  }

  public String getDockerLabel() {
    ':' + buildId
  }

  private String serviceFullName(String env, String podName) {
      return env+'-'+podName+'-'+serviceId_
  }
  
  public void deleteUser(String userName, String groupName)
  {
    def status = sh returnStatus:true, script:'aws iam get-user --user-name ' + userName
    
    if(status==0)
    {
      echo 'User ' + userName + " exists, deleting..."
        
      sh 'aws iam remove-user-from-group --user-name ' + userName +
          ' --group-name ' + groupName
      
      def keys = readJSON(text:
        sh(returnStdout: true, script: 'aws iam list-access-keys --user-name ' + userName))
      
      keys."AccessKeyMetadata".each
      {
        keySpec ->
          sh 'aws iam delete-access-key --access-key-id ' + keySpec."AccessKeyId" +
           ' --user-name ' + userName
      }
      
      sh 'aws iam delete-user --user-name ' + userName
    }
    else
    {
      echo 'User ' + userName + " does not exist."
    }
  }
  
  public CreateEnvironmentTypeTask  createEnvironmentTypeTask(String environmentType)
  {
    return new CreateEnvironmentTypeTask(this, environmentType)
      .withConfigGitRepo(configGitOrg, configGitRepo, configGitBranch)
  }
  
  public CreateEnvironmentTask  createEnvironmentTask(String environmentType, String environment,
    String region)
  {
    return new CreateEnvironmentTask(this, environmentType, environment, region)
      .withConfigGitRepo(configGitOrg, configGitRepo, configGitBranch)
  }
  
  public void loadConfig()
  {
    sh 'aws --version'
    
    echo 'git credentialsId: symphonyjenkinsauto url: https://github.com/' + configGitOrg + '/' + configGitRepo + '.git branch: ' + configGitBranch
    steps.git credentialsId: 'symphonyjenkinsauto', url: 'https://github.com/' + configGitOrg + '/' + configGitRepo + '.git', branch: configGitBranch
    
    def files = sh(script: "ls -1 config/environment", returnStdout: true)
    
    files.split('\n').each
    {
      String name ->
        if(name.endsWith(".json"))
        {
          echo 'Ignoring service config file ' + name
        }
        else
        {
          def config = readJSON file: 'config/environment/' + name + '/environmentType.json'
          environmentTypeConfig[name] = new EnvironmentTypeConfig(config."amazon")
          echo 'environmentType ' + name
        }
    }
    
    echo 'done environmentTypes'
  }
  
  public void report()
  {
    echo """
@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
@ buildId = ${buildId}
@ releaseVersion = ${release}
@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
"""
  }
  
  public void toolsPreFlight()
  {
    sh 'rm -rf *'

    echo """
@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
@ buildId = ${buildId}
@ releaseVersion = ${release}
@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
"""
    
    if(configGitRepo != null)
    {
      loadConfig()
    }
    
    if(!verifyCreds('dev', true))
    {
      abort("No dev credentials")
    }
  }
  
  private boolean qualifierIsSet()
  {
    return !("".equals(env_.buildId.trim()))
  }
  
  private void pushTo(String environmentType)
  {
    pushTo_[environmentType] = true
    verifyCreds_[environmentType] = true
  }
  
  private void deployTo(String environmentType, Purpose purpose = Purpose.Service)
  {
    deployTo_[environmentType] = purpose
    verifyCreds_[environmentType] = true
  }
  
  public Purpose getDeployTo(String environmentType)
  {
    Purpose p = deployTo_[environmentType]
    
    if(p == null)
      return Purpose.None
    else
      return p
  }
  
  public boolean getDoBuild()
  {
    return doBuild_
  }
  
  public void abort(String message)
  {
    steps_.error(message)
  }
  
  public void createDeployStation()
  {
    doBuild_ = false
    pullFrom_ = env_."environmentType"
    
    deployStation = new Station()
      .withPodNames(env_."podName")
      .withEnvironmentType(env_."environmentType")
      .withEnvironment(env_."environmentId")
      .withRegion(env_."regionId")
  }
  
  public void preflight()
  {
    echo """====================================
Preflight
echo 'FuguePipeline V3.7'
Build Action ${env_.buildAction}
"""
    switch(env_."dryRun")
    {
      case 'Dry Run':
        fugueDryRun_ = true;
        break;
      
      default:
        fugueDryRun_ = false;
        break;
    }
    
    switch(env_.buildAction)
    {
      case 'Build to QA':
        doBuild_ = true
        pushTo('dev')
        deployTo('smoke', Purpose.SmokeTest)
        deployTo('dev')
        pushTo('qa')
        deployTo('qa')
        targetEnvironmentType_ = 'qa'
        break;
        
      case 'Build to Dev':
        doBuild_ = true
        pushTo('dev')
        deployTo('smoke', Purpose.SmokeTest)
        deployTo('dev')
        targetEnvironmentType_ = 'dev'
        break;
        
      case 'Deploy to Dev':
        doBuild_ = false
        pullFrom_ = 'dev'
        deployTo('smoke', Purpose.SmokeTest)
        deployTo('dev')
        break;
        
      case 'Build to Smoke Test':
        doBuild_ = true
        pushTo('dev')
        targetEnvironmentType_ = 'dev'
        deployTo('smoke', Purpose.SmokeTest)
        deployTo('dev', Purpose.SmokeTest)
        break;
        
      case 'Promote Dev to QA':
        doBuild_ = false
        pullFrom_ = 'dev'
        pushTo('qa')
        deployTo('qa')
        targetEnvironmentType_ = 'qa'
        break;
        
      case 'Promote QA to Stage':
        doBuild_ = false
        pullFrom_ = 'qa'
        pushTo('stage')
        deployTo('stage')
        targetEnvironmentType_ = 'stage'
        break;
        
      case 'Promote Stage to Prod':
        doBuild_ = false
        pullFrom_ = 'stage'
        pushTo('prod')
        deployTo('prod')
        targetEnvironmentType_ = 'prod'
        break;
      
      case 'Deploy':
        createDeployStation()
        deployTo(env_."environmentType")
        targetEnvironmentType_ = env_."environmentType"
        break;
        
      case 'Undeploy Pod':
        createDeployStation()
        deployTo(env_."environmentType")
        targetEnvironmentType_ = env_."environmentType"
        fugueAction_ = "Undeploy"
        break;
        
      case 'Undeploy All':
        deployTo(env_."environmentType")
        targetEnvironmentType_ = env_."environmentType"
        fugueAction_ = "UndeployAll"
        break;
    }
    
    if(doBuild_)
    {
      if(qualifierIsSet())
      {
        abort('Do not set buildId for a build action.')
      }
    }
    else
    {
      if(qualifierIsSet())
      {
        withBuildId(env_.buildId.trim())
      }
      else
      {
        abort('buildId must be defined for a promotion action.')
      }
    }
    
    
    echo """====================================
doBuild         ${doBuild_}
pullFrom        ${pullFrom_}
pushTo          ${pushTo_}
deployTo        ${deployTo_}

fugueDryRun     ${fugueDryRun_}
fbuildAction    ${env_.buildAction}
===================================="""
    
    serviceGitOrg = env_.serviceRepoOrg
    serviceGitBranch = env_.serviceRepoBranch
    configGitOrg = env_.configRepoOrg
    configGitBranch = env_.configRepoBranch
    
    if(doBuild_)
    {
      sh 'rm -rf *'
    }
    
    if(configGitRepo != null)
    {
      loadConfig()

      sh 'ls -l config/service/' + serviceId_ + '/service.json'

      def service = readJSON file:'config/service/' + serviceId_ + '/service.json'

      service."containers".each { name, containerDef ->
        echo 'Container ' + name + ' = ' + containerDef


        Container ms = new Container(this)
            .withName(name)
            .withImage(containerDef."image")
            .withRole(containerDef."role")
            .withTenancy(Tenancy.parse(containerDef."tenancy"))
            .withContainerType(ContainerType.parse(containerDef."containerType"))
            
            .withEcsTemplate(containerDef."ecsTemplate")
       
          
        def v = containerDef."memory";
        
        if(v != null)
            ms.withMemory(v)
            
        v = containerDef."jvmHeap";
        
        if(v != null)
            ms.withJvmHeap(v)

        v = containerDef."port"
        
        if(v != null)
          ms.withPort(v)
          
        containerDef."environment".each { envName, envValue ->
          ms.withEnv(envName, envValue)
        }

        //echo 'ms=' + ms

        this.withContainer(ms)
      }

      if(releaseTrack != null)
      {
        def track = readJSON file:'config/track/' + releaseTrack + '.json'
  
        echo 'track is ' + track
        
  
        track."stations".each { stationDef ->
  
          Station ts = new Station()
              .withName(stationDef."name")
              .withEnvironmentType(stationDef."environmentType")
              .withEnvironment(stationDef."environment")
              .withRegion(stationDef."region")
              .withPrimaryEnvironment(stationDef."primaryEnvironment")
              .withPrimaryRegion(stationDef."primaryRegion")
  
          stationDef."podNames".each { podName ->
            ts.withPodNames(podName)
          }
  
          echo 'ts=' + ts
  
          this.withStation(ts)
        }
      }
    }
    
    if(doBuild_)
    {
      echo """
serviceGitOrg is ${serviceGitOrg}
serviceGitRepo is ${serviceGitRepo}
serviceGitBranch is ${serviceGitBranch}
"""
    
      steps.git credentialsId: 'symphonyjenkinsauto', url: 'https://github.com/' + serviceGitOrg + '/' + serviceGitRepo + '.git', branch: serviceGitBranch
    }
    
    
    verifyCreds_[pullFrom_] = true
    
    if(!verifyCreds('dev'))
    {
      abort("No dev credentials")
    }

    if(verifyCreds('qa'))
    {
      if(verifyCreds('stage'))
      {
        verifyCreds('prod')
      }
    }
    
    pullDockerImages()
  }
  
  public void verifyUserAccess(String credentialId, String environmentType = null)
  {
    echo """Verifying ${environmentType} access with credential ${credentialId}...
doBuild_        ${doBuild_}
toolsDeploy     ${toolsDeploy}
environmentType ${environmentType}
"""
    
    steps.withCredentials([[
      $class:             'AmazonWebServicesCredentialsBinding',
      accessKeyVariable:  'AWS_ACCESS_KEY_ID',
      credentialsId:      credentialId,
      secretKeyVariable:  'AWS_SECRET_ACCESS_KEY']])
    {
      aws_identity[credentialId] = readJSON(text: sh(returnStdout: true, script: 'aws sts get-caller-identity'))

      echo 'aws_identity[' + credentialId + ']=' + aws_identity[credentialId]

      
      //sh 'aws --region ' + awsRegion + ' ecs list-clusters'

      if(environmentType != null)
      {
        docker_repo[environmentType] = "${aws_identity[credentialId].Account}.dkr.ecr.us-east-1.amazonaws.com/${globalNamePrefix_}${serviceId_}/"
        
        service_map.values().each
        {
          Container ms = it
          
          switch(ms.containerType)
          {
            case ContainerType.SERVICE:
            case ContainerType.SCHEDULED:
            case ContainerType.INIT:
              try
              {
                  sh "aws --region ${awsRegion} ecr describe-repositories --repository-names ${globalNamePrefix_}${serviceId_}/${ms.image}"
              }
              catch(Exception e)
              {
                  echo 'Exception ' + e.toString()
                  sh "aws --region ${awsRegion} ecr create-repository --repository-name ${globalNamePrefix_}${serviceId_}/${ms.image}"
              }
              break;
              
            case ContainerType.LAMBDA:
            // Nothing to do here
              break;
          }
        }
        
        sh "set +x; echo 'Logging into docker repo'; `aws --region ${awsRegion} ecr get-login --no-include-email`"
                    
        if(doBuild_ && environmentType=='dev')
        {
                        
          sh "docker pull 189141687483.dkr.ecr.${awsRegion}.amazonaws.com/symphony-es/base-java8:latest"
          sh "docker pull 189141687483.dkr.ecr.${awsRegion}.amazonaws.com/symbase/java8:latest"
          sh "docker pull 189141687483.dkr.ecr.${awsRegion}.amazonaws.com/symbase/java10:latest"
          sh "docker pull 189141687483.dkr.ecr.${awsRegion}.amazonaws.com/symbase/java11:latest"
          
          
          steps.withCredentials([steps.file(credentialsId: 'maven-settings', variable: 'FILE')]) {
            sh 'cp $FILE /usr/share/maven/conf/settings.xml'
            
          } 
            
          if(!toolsDeploy)
          {
            sh "docker pull ${aws_identity[credentialId].Account}.dkr.ecr.us-east-1.amazonaws.com/${globalNamePrefix_}fugue/fugue-deploy:${FUGUE_VERSION}"
          }
        }
      }
    }
  }
  
  private String getCredentialName(String environmentType)
  {
    if(useRootCredentials)
    {
      // the dev-cicd user may not have been created yet, run this build as root.
      
      return "${globalNamePrefix_}fugue-${environmentType}-root"
    }
    else
    {
      return "${globalNamePrefix_}fugue-${environmentType}-cicd"
    }
  }
  
  public boolean verifyCreds(String environmentType, boolean force = false)
  {
    if(force)
      verifyCreds_[environmentType] = true;
      
    if(verifyCreds_[environmentType])
    {
      try
      {
        verifyUserAccess(getCredentialName(environmentType), environmentType);
        
        return true
      }
      catch(Exception e)
      {
        pushTo_[environmentType] = false
        deployTo_[environmentType] = Purpose.None
        echo "No valid credentials for environmentType ${environmentType}"
        
        return false
      }
    }
    else
    {
      echo "No need to verify credentials for environmentType ${environmentType}"
    }
  }
  
  public void deployInitContainers(Station station, Tenancy tenancy)
  {
    echo 'Init Containers for tenancy ' + tenancy
    
    service_map.values().each
    {
      Container ms = it
      
      echo 'Init Container? ' + ms
      
      if(ms.containerType == ContainerType.INIT && ms.tenancy == tenancy)
      {
        if(tenancy == Tenancy.SINGLE)
        {
          station.podNames.each
          {
            String podName = it
  
            echo 'Init ' + podName
            ms.deployInit(station, podName)
            echo 'DONE Init ' + podName
          }
        }
        else
        {
          echo 'Init Multi'
          ms.deployInit(station, null)
          echo 'DONE Init multi'
        }
      }
    }
  }
  
  public void deployInitContainers(Station station)
  {
    echo 'Init Containers'
    
    deployInitContainers(station, Tenancy.MULTI)
    
    deployInitContainers(station, Tenancy.SINGLE)
      
//    service_map.values().each
//    {
//      Container ms = it
//      
//      if(ms.containerType == ContainerType.INIT && ms.tenancy == Tenancy.MULTI)
//      {
//          echo 'Init MULTI ' + ms.toString()
//          ms.deployInit(station, null)
//          echo 'DONE Init MULTI ' + ms.name
//      }
//    }
//    
//    service_map.values().each
//    {
//      Container ms = it
//      
//      if(ms.containerType == ContainerType.INIT && ms.tenancy == Tenancy.SINGLE)
//      {
//        station.podNames.each
//        {
//          String podName = it
//       
//          echo 'Init ' + podName + ' ' + ms.toString()
//          
//          ms.deployInit(station, podName)
//        }
//      }
//    }
  }
  
  public void deployServiceContainers(Station station)
  {
    echo 'Runtime Containers'
    
    service_map.values().each
    {
      Container ms = it
      
      if(ms.containerType == ContainerType.SERVICE || ms.containerType == ContainerType.SCHEDULED)
      {
        switch(ms.tenancy)
        {
          case Tenancy.SINGLE:
            station.podNames.each
            {
              String podName = it
              
              echo 'Runtime ' + podName + ' ' + ms.toString()
              ms.registerTaskDef(station, podName)
            }
            break
  
          case Tenancy.MULTI:
            echo 'Runtime MULTI ' + ms.toString()
              ms.registerTaskDef(station, null)
              break
        }
      }
    }
  }
  
  public void deployInitContainers(String stationName)
  {
    Station station = stationMap.get(stationName)
 
    deployInitContainers(station)
  }
  
  public boolean deployStation(String stationName, Purpose requiredPurpose = Purpose.Service)
  {
    Station station = stationName == null ? deployStation: stationMap.get(stationName)
    
    echo "Deploy for stationName=${stationName} station=${station} podNames=${station.podNames}"
    
    echo "ValidPurpose=${getDeployTo(station.environmentType)} requiredPurpose=${requiredPurpose}"
    if(getDeployTo(station.environmentType).isValidFor(requiredPurpose))
    {
      echo "OK, lets do this!"
      
//      if("Deploy".equals(fugueAction_))
//      {
//        boolean doMultiTenantConfig = true
//        
//        station.podNames.each {
//          String podName = it
//          
//          echo 'for environmentType=' + station.environmentType +
//          ', environment=' + station.environment + ', podName ' + podName
//          
//          deployConfig(station, podName, 'DeployConfig')
//          doMultiTenantConfig = false
//  
//        }
//        
//        if(doMultiTenantConfig)
//        {
//          deployConfig(station, null, 'DeployConfig')
//        }
//      }
        
      if(toolsDeploy) 
      {
        String accountId = getCredentialName(station.environmentType)
    
        FugueDeploy deploy = new FugueDeploy(this, 
          "DeployConfig",
          awsRegion)
            .withConfigGitRepo(configGitOrg, configGitRepo, configGitBranch)
            .withTrack(releaseTrack)
            .withStation(station)
            .withServiceName(serviceId_)
            .withBuildId(buildId)
            .withDockerLabel(':' + buildId)
          
        deploy.execute()
      }
      else
      {
        deployInitContainers(station)
      
        if("Deploy".equals(fugueAction_)) // no point doing this if it's a delete
        {
          // this just creates task defs
          deployServiceContainers(station)
        }
        
        // this calls FugueDeploy to actually deploys service containers
        fugueDeployStation(releaseTrack, station)
        
        
//        station.podNames.each {
//            String podName = it
//            
//            echo 'for environmentType=' + station.environmentType +
//            ', environment=' + station.environment + ', podName ' + podName
//            
//            deployConfig(station, podName, 'Deploy')
//    
//          }
      }
      return true
    }
    else
    {
      echo 'No access for environmentType ' + station.environmentType + ', skipped.'
      return false
    }
  }


  public void pullDockerImages()
  {
    if(pullFrom_ != null)
    {
      echo 'Pull Images from ' + pullFrom_
      
      service_map.values().each
      {
        Container ms = it
        
        switch(ms.containerType)
        {
          case ContainerType.SERVICE:
          case ContainerType.SCHEDULED:
          case ContainerType.INIT:
            String repo = docker_repo[pullFrom_]
        
            if(repo == null)
              throw new IllegalStateException("Unknown environment type ${pullFrom_}")
            
            String localImage = ms.image + ':' + release
            String remoteImage = repo + ms.image + ':' + buildId
            
            sh 'docker pull ' + remoteImage
            break;
            
          case ContainerType.LAMBDA:
            steps.withCredentials([[
            $class:             'AmazonWebServicesCredentialsBinding',
            accessKeyVariable:  'AWS_ACCESS_KEY_ID',
            credentialsId:      getCredentialName(pullFrom_),
            secretKeyVariable:  'AWS_SECRET_ACCESS_KEY']])
            {
              sh 'aws sts get-caller-identity'
              //sh "aws s3 cp s3://${globalNamePrefix_}fugue-${pullFrom_}-${awsRegion}-config/lambda/${serviceId_}/${ms.image}-${buildId}.jar ${ms.image}/target/${ms.image}-${buildId}.jar"
              sh "aws s3 sync s3://${globalNamePrefix_}fugue-${pullFrom_}-${awsRegion}-config/lambda/${serviceId_} ${ms.image}/target --exclude \"*\" --include ${ms.image}-${buildId}.jar"
            }
            break;
        }
      }
    }
  }
  
  public void pushDockerImages(String environmentType)
  {
    echo 'Push Images for ' + environmentType
    
    if(pushTo_[environmentType])
    {
      service_map.values().each
      {
        Container ms = it
        
        switch(ms.containerType)
        {
          case ContainerType.SERVICE:
          case ContainerType.SCHEDULED:
          case ContainerType.INIT:
            String pushRepo = docker_repo[environmentType]
            
            if(pushRepo == null)
              throw new IllegalStateException("Unknown environment type ${environmentType}")
            
            
    
            if(pullFrom_ == null)
            {
              String localImage = ms.image + ':' + release
              String remoteImage = pushRepo + ms.image + ':' + buildId
              
              sh """
docker tag ${localImage} ${remoteImage}
docker push ${remoteImage}
"""
            }
            else
            {
              String pullRepo = docker_repo[pullFrom_]
              
              String baseImage = ms.image + ':' + buildId
              String localImage = pullRepo + baseImage
              String remoteImage = pushRepo + baseImage
              
              sh """
docker tag ${localImage} ${remoteImage}
docker push ${remoteImage}
"""
            }
            break;
            
          case ContainerType.LAMBDA:
            if(pullFrom_ == null)
            {
              steps.withCredentials([[
              $class:             'AmazonWebServicesCredentialsBinding',
              accessKeyVariable:  'AWS_ACCESS_KEY_ID',
              credentialsId:      getCredentialName(environmentType),
              secretKeyVariable:  'AWS_SECRET_ACCESS_KEY']])
              {
                sh 'aws sts get-caller-identity'
                sh "aws s3 cp ${ms.image}/target/${ms.image}-${release}.jar s3://${globalNamePrefix_}fugue-${environmentType}-${awsRegion}-config/lambda/${serviceId_}/${ms.image}-${buildId}.jar"
              }
            }
            else
            {
              steps.withCredentials([[
              $class:             'AmazonWebServicesCredentialsBinding',
              accessKeyVariable:  'AWS_ACCESS_KEY_ID',
              credentialsId:      getCredentialName(environmentType),
              secretKeyVariable:  'AWS_SECRET_ACCESS_KEY']])
              {
                sh 'aws sts get-caller-identity'
                sh "aws s3 cp ${ms.image}/target/${ms.image}-${buildId}.jar s3://${globalNamePrefix_}fugue-${environmentType}-${awsRegion}-config/lambda/${serviceId_}/${ms.image}-${buildId}.jar"
              }
            }
            break;
        }
      }
    }
    else
    {
      if(environmentType.equals(targetEnvironmentType_))
        throw new RuntimeException('Cannot push to target environment type ' + targetEnvironmentType_)
        
      echo 'No access for environmentType ' + environmentType + ', skipped.'
    }
  }
  
  public void pushDockerToolCandidate(String environmentType, String name)
  {
    if(doBuild_)
    {
      echo 'Push Tool candidate for ' + name
      
      String repo = docker_repo[environmentType]
      
      if(repo == null)
        throw new IllegalStateException("Unknown environment type ${environmentType}")
      
      String localImage = name + ':' + release
      String remoteImage = repo + name + ':' + buildId
      
      echo 'localImage=' + localImage
      echo 'remoteImage=' + remoteImage
      
      sh "docker tag ${localImage} ${remoteImage}"
      sh "docker push ${remoteImage}"
    }
  }
  
  public void pushDockerToolImage(String environmentType, String name)
  {
    echo 'pushDockerToolImage(environmentType=' + environmentType + ', name=' + name + ')'
    echo 'deployTo_[' + environmentType + '] = ' + deployTo_[environmentType]
    echo 'pushTo_[' + environmentType + '] = ' + pushTo_[environmentType]
    
    if(doBuild_ && deployTo_[environmentType] == Purpose.Service)
    {
      echo 'Push Tool Image for ' + name
      
      String repo = docker_repo[environmentType]
      
      if(repo == null)
        throw new IllegalStateException("Unknown environment type ${environmentType}")
      
      String localImage = name + ':' + release
      String remoteImage = repo + name + ':' + FUGUE_VERSION
      
      sh 'docker tag ' + localImage + ' ' + remoteImage
      sh 'docker push ' + remoteImage
    }
  }
  
  public void sleep(long time)
  {
    Thread.sleep(time)
  }

  public void deployConfig(Station station, String podName, String task)
  {
    String logGroup
    String accountId = getCredentialName(station.environmentType)
    
    FugueDeploy deploy = new FugueDeploy(this, task,
      awsRegion)
        .withConfigGitRepo(configGitOrg, configGitRepo, configGitBranch)
        .withStation(station)
        .withPodName(podName)
        .withServiceName(serviceId_)
    
    if(toolsDeploy)
      deploy.withDockerLabel(':' + buildId)
      
    deploy.execute() 
  }
  
  public void fugueDeployStation(String releaseTrack, Station station)
  {
    echo "fugueDeployStation(releaseTrack=${releaseTrack}, station=${station})"
    
    String accountId = getCredentialName(station.environmentType)
    
    FugueDeploy deploy = new FugueDeploy(this, 
      fugueAction_,
      awsRegion)
        .withConfigGitRepo(configGitOrg, configGitRepo, configGitBranch)
        .withTrack(releaseTrack)
        .withStation(station)
        .withServiceName(serviceId_)
        .withBuildId(buildId)
    
    if(station.name == null)
    {
      for(String name : station.getPodNames())
      {
        deploy.withPodName(name)
        echo "add podName ${name}"
      }
    }
    
    if(toolsDeploy)
      deploy.withDockerLabel(':' + buildId)
      
    deploy.execute()
  }
  
  String logGroupName(Station station, String podName)
  {
    return logGroupName(station, podName, serviceId_)
  }
  
  String logGroupName(Station station, String podName, String serviceId)
  {
    if(station.logGroupName == null) {
      return logGroupName(station.environmentType, station.environment, podName, serviceId)
    }
    else {
      return station.logGroupName;
    }
  }
  
  String logGroupName(String environmentType, String environment, String podName, String serviceId)
  {
    
      String name = globalNamePrefix_ + environmentType + '-' + environment + '-';
      
      return podName == null ? name + serviceId : name + podName + '-' + serviceId;
    
  }
  
  String createLogGroup(Station station, String podName)
  {
    createLogGroup(logGroupName(station, podName));
  }
  
  String createLogGroup(String name)
  {
    if(logGroupSet.add(name))
    {
      echo 'Create log group ' + name + ' if necessary'
 
        def logDesc = readJSON(text: 
          sh(returnStdout: true, script: 'aws --region ' + awsRegion + ' logs describe-log-groups --log-group-name '+ name)
      )
      
      if(logDesc.'logGroups'.size() ==0)
      {
        echo 'Create log group ' + name
        sh 'aws --region ' + awsRegion + ' logs create-log-group --log-group-name '+ name
        sh 'aws --region ' + awsRegion + ' logs put-retention-policy --log-group-name '+ name +' --retention-in-days 14'
      }
      else
      {
        echo 'Log group ' + name + ' size=' + logDesc.'logGroups'.size()
      }
    }
    else
    {
      echo 'Log group ' + name + ' already created.'
    }
    
    return name
  }

//  private static final String record_set_template =
//      '''{
//    "Comment": "DNS for ${TENANT_ID} during ${SERVICE_NAME} service",
//    "Changes": [
//      {
//        "Action": "UPSERT",
//        "ResourceRecordSet": {
//          "Name": "${TENANT_ID}.${DNS_SUFFIX}.",
//          "Type": "CNAME",
//          "TTL": 300,
//          "ResourceRecords": [
//            {
//              "Value": "${ALB_DNS}"
//            }
//          ]
//        }
//      }
//    ]
//  }'''
  
  public static def buildIdParameters(env, steps)
  {
    def list = [
    steps.string(name: 'buildId',    defaultValue: Default.value(env,  'buildId',    ''),      description: 'The build ID for promotion.'),
    ]
     
    return list
  }
  
  public static def sourceVersionParameters(env, steps)
  {
    def list = [
    steps.string(name: 'serviceRepoOrg',    defaultValue: Default.value(env,  'serviceRepoOrg',    'SymphonyOSF'),  description: 'GitHub organization (fork) for service source code repo.'),
    steps.string(name: 'serviceRepoBranch', defaultValue: Default.value(env,  'serviceRepoBranch', 'master'),description: 'GitHub branch for service source code repo.'),
   ]
     
    return list
  }
  
  public static def configVersionParameters(env, steps)
  {
    def list = [
    steps.string(name: 'configRepoOrg',     defaultValue: Default.value(env,  'configRepoOrg',     'SymphonyOSF'),  description: 'GitHub organization (fork) for config repo.'),
    steps.string(name: 'configRepoBranch',  defaultValue: Default.value(env,  'configRepoBranch',  'master'), description: 'GitHub branch for config repo.')
   ]
     
    return list
  }
  
  public static def parameters(env, steps, extras = null)
  {
    def list = [
    steps.choice(name: 'buildAction',       choices:      Default.choice(env, 'buildAction', ['Build to Smoke Test', 'Build to Dev', 'Build to QA', 'Deploy to Dev', 'Promote Stage to Prod', 'Promote QA to Stage', 'Promote Dev to QA']), description: 'Action to perform'),
    ]
   
    list.addAll(buildIdParameters(env, steps))
    list.addAll(sourceVersionParameters(env, steps))
    list.addAll(configVersionParameters(env, steps))
   
    if(extras != null)
     list.addAll(extras)
     
    return [
  steps.parameters(list)
]
  }
  
  public static def deployParameters(env, steps, extras = null)
  {
    def list = [
    steps.choice(name: 'buildAction',       choices:      Default.choice(env, 'buildAction',      ['Deploy', 'Undeploy Pod', 'Undeploy All']), description: 'Action to perform'),
    steps.choice(name: 'dryRun',            choices:      Default.choice(env, 'dryRun',           ['Execute', 'Dry Run']),    description: 'Dry run or actually do it'),
    steps.string(name: 'podName',           defaultValue: Default.value(env,  'podName',          'sym-ac6-dev-chat-glb-1'),  description: 'Pod Name.'),
    steps.string(name: 'environmentType',   defaultValue: Default.value(env,  'environmentType',  'dev'),                     description: 'Environment Type ID.'),
    steps.string(name: 'environmentId',     defaultValue: Default.value(env,  'environmentId',    's2dev2'),                  description: 'Environment  ID.'),
    steps.string(name: 'regionId',          defaultValue: Default.value(env,  'regionId',         'us-east-1'),               description: 'Service ID.'),
    ]
   
    list.addAll(buildIdParameters(env, steps))
    list.addAll(sourceVersionParameters(env, steps))
    list.addAll(configVersionParameters(env, steps))
   
    if(extras != null)
     list.addAll(extras)
     
    return [
  steps.parameters(list)
]
  }
  
  
  public void validateRootPolicy(String accountId, String environmentType)
  {
    boolean ok = false
    String policyArn = "arn:aws:iam::${aws_identity[accountId].Account}:policy/sym-s2-fugue-${environmentType}-root-policy"
    
    def policy = readJSON(text:
      sh(returnStdout: true, script: "aws --region ${awsRegion} iam get-policy --policy-arn  ${policyArn}"))
    
    def policyVersion = readJSON(text:
      sh(returnStdout: true, script: "aws --region ${awsRegion} iam get-policy-version --policy-arn  ${policyArn} --version-id ${policy.'Policy'.'DefaultVersionId'}"))
    
    
    policyVersion."PolicyVersion"."Document"."Statement".each
    {
      statement ->
        if('FugueAdmin'.equals(statement."Sid"))
        {
          statement."Action".each
          {
            action ->
            
            if('iam:PassRole'.equals(action))
            {
              echo 'thats it!'
              
              ok = true
            }
          }
        }
    }
    
    if(ok)
    {
      echo "Root policy is good, nothing more to do."
    }
    else
    {
      echo "Updating root policy..."
      
      def policyVersionList = readJSON(text:
        sh(returnStdout: true, script: "aws --region ${awsRegion} iam list-policy-versions --policy-arn  ${policyArn}"))
      
      int cnt = 0
      def latestVersion
      
      policyVersionList."Versions".each
      {
        version ->
          if(version."IsDefaultVersion")
          {
            echo 'Default version'
          }
          else
          {
            cnt++
            
            if(latestVersion == null || latestVersion."CreateDate" > version."CreateDate")
            {
              latestVersion = version
            }
          }
      }
      
      if(cnt > 3)
      {
        echo 'OK we will delete ' + latestVersion
        
        sh "aws --region ${awsRegion} iam delete-policy-version --policy-arn  ${policyArn} --version-id ${latestVersion.'VersionId'}"
      }
      else
      {
        echo 'Only ' + cnt + ' old versions, no need to delete one'
      }
      
      def newPolicyVersion = readJSON(text:
        sh(returnStdout: true, script: "aws --region ${awsRegion} iam create-policy-version --set-as-default --policy-arn  ${policyArn} --policy-document \'${rootPolicyDocument}\'"))
    }
  }
  
  private static final String rootPolicyDocument='''{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "AllResources",
            "Effect": "Allow",
            "Action": [
                "logs:DescribeLogGroups",
                "ecs:ListClusters",
                "ecs:DescribeClusters",
                "ecs:CreateCluster",
                "ecs:RegisterTaskDefinition",
                "ecs:DeregisterTaskDefinition",
                "ecs:DescribeTasks",
                "ecs:DescribeTaskDefinition",
                "ecs:ListTaskDefinitions",
                "secretsmanager:CreateSecret"
            ],
            "Resource": "*"
        },
        {
            "Sid": "LogsAll",
            "Effect": "Allow",
            "Action": "logs:*",
            "Resource": [
                "arn:aws:logs:*:*:log-group:sym-s2-fugue:*:*",
                "arn:aws:logs:*:*:log-group:sym-s2-fugue"
            ]
        },
        {
            "Sid": "ContainerRegistryS2",
            "Effect": "Allow",
            "Action": [
                "ecr:GetDownloadUrlForLayer",
                "ecr:BatchDeleteImage",
                "ecr:UploadLayerPart",
                "ecr:ListImages",
                "ecr:DeleteRepository",
                "ecr:PutImage",
                "ecr:SetRepositoryPolicy",
                "ecr:BatchGetImage",
                "ecr:CompleteLayerUpload",
                "ecr:DescribeImages",
                "ecr:DescribeRepositories",
                "ecr:DeleteRepositoryPolicy",
                "ecr:InitiateLayerUpload",
                "ecr:BatchCheckLayerAvailability",
                "ecr:GetRepositoryPolicy"
            ],
            "Resource": "arn:aws:ecr:*:*:repository/sym-s2-*"
        },
        {
            "Sid": "ContainerRegistryES",
            "Effect": "Allow",
            "Action": [
                "ecr:GetDownloadUrlForLayer",
                "ecr:ListImages",
                "ecr:BatchGetImage",
                "ecr:DescribeImages",
                "ecr:DescribeRepositories",
                "ecr:BatchCheckLayerAvailability",
                "ecr:GetRepositoryPolicy"
            ],
            "Resource": [
              "arn:aws:ecr:*:*:repository/symphony-es",
              "arn:aws:ecr:*:*:repository/symphony-es/*",
              "arn:aws:ecr:*:*:repository/symbase",
              "arn:aws:ecr:*:*:repository/symbase/*"
              ]
        },
        {
            "Sid": "ContainerRegistryAll",
            "Effect": "Allow",
            "Action": [
                "ecr:CreateRepository",
                "ecr:GetAuthorizationToken"
            ],
            "Resource": "*"
        },
        {
            "Sid": "EcsRole",
            "Effect": "Allow",
            "Action": [
                "iam:GetRole",
                "iam:PassRole",
                "iam:ListAttachedRolePolicies"
            ],
            "Resource": [
              "arn:aws:iam::*:role/ecsTaskExecutionRole"
            ]
        },
        {
            "Sid": "RootAdmin",
            "Effect": "Allow",
            "Action": [
                "iam:GetRole",
                "iam:PassRole",
                "iam:CreateRole",
                "iam:AttachRolePolicy",
                "iam:ListAttachedRolePolicies"
            ],
            "Resource": [
              "arn:aws:iam::*:role/sym-s2-*-root-role",
              "arn:aws:iam::*:role/sym-s2-*-admin-role"
            ]
        },
        {
            "Sid": "FugueAdmin",
            "Effect": "Allow",
            "Action": [
                "iam:GetRole",
                "iam:GetPolicyVersion",
                "iam:ListPolicyVersions",
                "iam:CreatePolicyVersion",
                "iam:DeletePolicyVersion",
                "iam:GetPolicy",
                "iam:CreatePolicy",
                "iam:CreateAccessKey",
                "iam:DeleteAccessKey",
                "iam:CreateRole",
                "iam:PassRole",
                "iam:ListAttachedRolePolicies",
                "iam:AttachRolePolicy",
                "iam:GetUser",
                "iam:CreateUser",
                "iam:DeleteUser",
                "iam:ListGroupsForUser",
                "iam:CreateGroup",
                "iam:AttachGroupPolicy",
                "iam:ListAttachedGroupPolicies",
                "iam:GetGroup",
                "iam:AddUserToGroup",
                "iam:RemoveUserFromGroup",
                "ecs:RunTask",
                "iam:ListAccessKeys",
                "secretsmanager:GetSecretValue",
                "secretsmanager:PutSecretValue",
                "iam:UpdateAssumeRolePolicy"
            ],
            "Resource": [
                "arn:aws:ecs:*:*:task-definition/sym-s2-fugue-*",
                "arn:aws:iam::*:policy/sym-s2-fugue-*",
                "arn:aws:iam::*:role/sym-s2-fugue-*",
                "arn:aws:iam::*:user/sym-s2-fugue-*",
                "arn:aws:iam::*:group/sym-s2-fugue-*",
                "arn:aws:secretsmanager:*:*:secret:sym-s2-fugue-*"
            ]
        },
        {
            "Sid": "S3",
            "Effect": "Allow",
            "Action": [
                "s3:CreateBucket",
                "s3:PutEncryptionConfiguration",
                "s3:GetBucketLocation"
            ],
            "Resource": [
                "arn:aws:s3:::sym-s2-fugue-*"
            ]
        }
    ]
}'''
  
  public def createRole(String accountId, String policyName, String roleName)
  {
    String policyArn = 'arn:aws:iam::' + aws_identity[accountId].Account + ':policy/' + policyName
    
    return createRoleByArn(accountId, policyArn, roleName)
  }
  
  public def createRoleByArn(String accountId, String policyArn, String roleName)
  {
    def status = sh returnStatus:true, script:"aws --region ${awsRegion} iam get-role --role-name ${roleName}"
    boolean attachPolicy = true
    
    if(status==0)
    {
      echo 'Role ' + roleName + " exists."
      
      def attachedPolicies = readJSON(text:
        sh(returnStdout: true, script: "aws --region ${awsRegion} iam list-attached-role-policies --role-name ${roleName}"))
      
      attachedPolicies."AttachedPolicies".each
      {
        policySpec ->
          if(policyArn.equals(policySpec."PolicyArn"))
          {
            echo "Existing role ${roleName} already has policy ${policyArn} attached, nothing more to do."
            
            attachPolicy = false
          }
      }
    }
    else
    {
      echo "Creating role ${roleName}..."
      
      sh "aws --region ${awsRegion} iam create-role --role-name ${roleName} --assume-role-policy-document '${trust_relationship_document}'"
    }    
    
    if(attachPolicy)
    {
      sh "aws --region ${awsRegion} iam attach-role-policy --role-name ${roleName} --policy-arn ${policyArn}"
      
      echo 'Waiting for 20 seconds for the new role to become active....'
      Thread.sleep(20000)
      echo 'OK'
    }
  }

      
  private static final String trust_relationship_document =
  '''{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "",
      "Effect": "Allow",
      "Principal": {
        "Service": "ecs-tasks.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}''' 
}
