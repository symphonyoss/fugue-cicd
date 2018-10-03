package org.symphonyoss.s2.fugue.cicd.v1

import org.apache.commons.lang3.text.StrSubstitutor;
import org.jenkinsci.plugins.workflow.cps.DSL
import org.jenkinsci.plugins.workflow.cps.EnvActionImpl

import java.util.Map.Entry
import java.util.Random

class FuguePipeline extends JenkinsTask implements Serializable
{
  private EnvActionImpl environ
  private DSL           steps
  
  private Map tenants = [:]

  private Map aws_identity = [:]
  private Map docker_repo = [:]
  private Map<String, EnvironmentTypeConfig> environmentTypeConfig = [:]
  private Map<String, Container> service_map = [:]
  private Map<String, Station> tenant_stage_map = [:]
  private Map<String, String> role_map = [:]
  private Set<String> logGroupSet = []
  private String symbase = 'symbase'


  private String servicename
  private String symteam = 'sym'

  private String awsRegion = 'us-east-1'
  private String release
  private String buildQualifier_
  private String servicePath
  private boolean toolsDeploy = false
  private boolean useRootCredentials = false
  
  private boolean               doBuild_
  private Map<String, Boolean>  pushTo_ = [:]
  private Map<String, Purpose>  deployTo_ = [:]
  private String                pullFrom_
  
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
    
    buildQualifier_ = new Date().format('yyyyMMdd-HHmmss-') + new Random().nextInt(9999)
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
      "\n  symteam              =" + symteam +
      "\n  release              =" + release +
      "\n  buildQualifier_       =" + buildQualifier_ +
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
      this.servicename = n
      return this
  }
  
  public FuguePipeline withPath(String n) {
    this.servicePath = n
    return this
  }

  public FuguePipeline withTeam(String t) {
      this.symteam = t
      return this
  }
  
  public FuguePipeline withStation(Station tenantStage) {
    // It should be an error to include the same tenant in the same environment twice in one tenantStage or
    // in separate tenantStages
    tenant_stage_map.put(tenantStage.name, tenantStage)
    return this
  }
  
  public FuguePipeline withRelease(String v) {
      release = v
      return this
  }
  
  public FuguePipeline withBuildQualifier(String v) {
      buildQualifier_ = v
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
  
  public String getBuildQualifier()
  {
    return buildQualifier_;
  }

  public String getDockerLabel() {
    ':' + release + '-' + buildQualifier_
  }

  private String serviceFullName(String env, String tenant) {
      return env+'-'+tenant+'-'+servicename
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
    String realm, String region)
  {
    return new CreateEnvironmentTask(this, environmentType, environment, realm, region)
      .withConfigGitRepo(configGitOrg, configGitRepo, configGitBranch)
  }
  
  public void loadConfig()
  {
    echo 'FuguePipeline V3'
    
    sh 'aws --version'
    
    echo 'git credentialsId: symphonyjenkinsauto url: https://github.com/' + configGitOrg + '/' + configGitRepo + '.git branch: ' + configGitBranch
    steps.git credentialsId: 'symphonyjenkinsauto', url: 'https://github.com/' + configGitOrg + '/' + configGitRepo + '.git', branch: configGitBranch
    
    def files = sh(script: "ls -1 config/environment", returnStdout: true)
    
    files.split('\n').each
    {
      name ->
        def config = readJSON file: 'config/environment/' + name + '/environmentType.json'
        environmentTypeConfig[name] = new EnvironmentTypeConfig(config."amazon")
        echo 'environmentType ' + name
    }
    
    echo 'done environmentTypes'
  }
  
  public void report()
  {
    echo """
@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
@ buildQualifier = ${buildQualifier_}
@ releaseVersion = ${release}
@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
"""
  }
  
  public void toolsPreFlight()
  {
    sh 'rm -rf *'

    echo """
@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
@ buildQualifier = ${buildQualifier_}
@ releaseVersion = ${release}
@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
"""
    
    if(configGitRepo != null)
    {
      loadConfig()
    }
    
    if(!verifyCreds('dev'))
    {
      abort("No dev credentials")
    }
  }
  
  private boolean qualifierIsSet()
  {
    return !("".equals(env_.buildQualifier.trim()) && "".equals(env_.releaseVersion.trim()))
  }
  
  private void pushTo(String environmentType)
  {
    pushTo_[environmentType] = true
  }
  
  private void deployTo(String environmentType, Purpose purpose = Purpose.Service)
  {
    deployTo_[environmentType] = purpose
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
  
  public void preflight()
  {
    echo """====================================
Preflight V2
Build Action ${env_.buildAction}
"""
    
    switch(env_.buildAction)
    {
      case 'Build to QA':
        doBuild_ = true
        pushTo('dev')
        deployTo('dev')
        pushTo('qa')
        deployTo('qa')
        break;
        
      case 'Deploy to Dev':
        doBuild_ = false
        pullFrom_ = 'dev'
        deployTo('dev')
        break;
        
      case 'Build to Smoke Test':
        doBuild_ = true
        pushTo('dev')
        deployTo('dev', Purpose.SmokeTest)
        break;
        
      case 'Promote Dev to QA':
        doBuild_ = false
        pullFrom_ = 'dev'
        pushTo('qa')
        deployTo('qa')
        break;
        
      case 'Promote QA to Prod':
        doBuild_ = false
        pullFrom_ = 'qa'
        pushTo('prod')
        deployTo('prod')
        break;
    }
    
    if(doBuild_)
    {
      if(qualifierIsSet())
      {
        abort('Do not set releaseVersion or buildQualifier for a build action.')
      }
    }
    else
    {
      if(qualifierIsSet())
      {
        withRelease(env_.releaseVersion.trim())
        withBuildQualifier(env_.buildQualifier.trim())
      }
      else
      {
        abort('releaseVersion and buildQualifier must be defined for a promotion action.')
      }
    }
    
    
    echo """====================================
doBuild      ${doBuild_}
pullFrom     ${pullFrom_}
pushTo       ${pushTo_}
deployTo     ${deployTo_}
===================================="""
    
    serviceGitOrg = env_.serviceRepoOrg
    serviceGitBranch = env_.serviceRepoBranch
    configGitOrg = env_.configRepoOrg
    configGitBranch = env_.configRepoBranch
    
       
    sh 'rm -rf *'
    
    if(configGitRepo != null)
    {
      loadConfig()

      sh 'ls -l config/service/' + servicename + '/service.json'

      def service = readJSON file:'config/service/' + servicename + '/service.json'

      echo 'service is ' + service

      service."containers".each { name, containerDef ->
        echo 'Container ' + name + ' = ' + containerDef


        Container ms = new Container(this)
            .withName(name)
            .withRole(containerDef."role")
            .withTenancy(containerDef."tenancy" == "SINGLE" ? Tenancy.SingleTenant : Tenancy.MultiTenant)
            .withContainerType(containerDef."containerType" == "INIT" ? ContainerType.Init : ContainerType.Runtime)
            .withPort(containerDef."port")
            .withEcsTemplate(containerDef."ecsTemplate")

        containerDef."environment".each { envName, envValue ->
          ms.withEnv(envName, envValue)
        }

        //echo 'ms=' + ms

        this.withContainer(ms)
      }


      def track = readJSON file:'config/track/' + releaseTrack + '.json'

      echo 'track is ' + track
      

      track."stations".each { stationDef ->

        Station ts = new Station()
            .withName(stationDef."name")
            .withEnvironmentType(stationDef."environmentType")
            .withEnvironment(stationDef."environment")
            .withRealm(stationDef."realm")
            .withRegion(stationDef."region")
            .withPrimaryEnvironment(stationDef."primaryEnvironment")
            .withPrimaryRegion(stationDef."primaryRegion")


        stationDef."tenants".each { tenant ->
          ts.withTenants(tenant)
        }

        echo 'ts=' + ts

        this.withStation(ts)
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
    
    if(!verifyCreds('dev'))
    {
      abort("No dev credentials")
    }

    if(verifyCreds('qa'))
    {
      verifyCreds('stage')
    }
    
    pullDockerImages()
  }
  
  public void verifyUserAccess(String credentialId, String environmentType = null)
  {
    echo 'Verifying ' + environmentType + ' access with credential ' + credentialId + '...'
    
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
        docker_repo[environmentType] = aws_identity[credentialId].Account+'.dkr.ecr.us-east-1.amazonaws.com/'+symteam+'/'
        
        service_map.values().each
        {
          Container ms = it
            
          try
          {
              sh 'aws --region ' + awsRegion + ' ecr describe-repositories --repository-names '+symteam+'/'+ms.name
          }
          catch(Exception e)
          {
              echo 'Exception ' + e.toString()
              sh 'aws --region ' + awsRegion + ' ecr create-repository --repository-name '+symteam+'/'+ms.name
          }
        }
        
        sh "set +x; echo 'Logging into docker repo'; `aws --region " + awsRegion + " ecr get-login --no-include-email`"
                    
        if(doBuild_ && environmentType=='dev')
        {
                        
          sh 'docker pull 189141687483.dkr.ecr.' + awsRegion + '.amazonaws.com/symphony-es/base-java8:latest'
          
          
          steps.withCredentials([steps.file(credentialsId: 'maven-settings', variable: 'FILE')]) {
            sh 'cp $FILE /usr/share/maven/conf/settings.xml'
            
          }
          
          if(!toolsDeploy)
          {
            sh 'docker pull ' + aws_identity[credentialId].Account+'.dkr.ecr.us-east-1.amazonaws.com/fugue/' + 'fugue-deploy:latest'
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
      
      return 'fugue-' + environmentType + '-root'
    }
    else
    {
      return 'fugue-' + environmentType + '-cicd'
    }
  }
  
  public boolean verifyCreds(String environmentType)
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
  
  public void deployInitContainers(Station tenantStage)
  {
    echo 'Init Containers'
    
    service_map.values().each
    {
      Container ms = it
      
      if(ms.containerType == ContainerType.Init && ms.tenancy == Tenancy.MultiTenant)
      {
          echo 'Init MULTI ' + ms.toString()
          ms.deployInit(tenantStage, null)
          echo 'DONE Init MULTI ' + ms.name
      }
    }
    
    service_map.values().each
    {
      Container ms = it
      
      if(ms.containerType == ContainerType.Init && ms.tenancy == Tenancy.SingleTenant)
      {
        tenantStage.tenants.each
        {
          String tenant = it
       
          echo 'Init ' + tenant + ' ' + ms.toString()
          
          ms.deployInit(tenantStage, tenant)
        }
      }
    }
  }
  
  public void deployServiceContainers(Station tenantStage)
  {
    echo 'Runtime Containers'
    
    service_map.values().each
    {
      Container ms = it
      
      if(ms.containerType == ContainerType.Runtime)
      {
        switch(ms.tenancy)
        {
          case Tenancy.SingleTenant:
            tenantStage.tenants.each
            {
              String tenant = it
              
              echo 'Runtime ' + tenant + ' ' + ms.toString()
              ms.deployService(tenantStage, tenant)
            }
            break
  
          case Tenancy.MultiTenant:
            echo 'Runtime MULTI ' + ms.toString()
              ms.deployService(tenantStage, null)
              break
        }
      }
    }
  }
  
  public void deployInitContainers(String tenantStageName)
  {
    Station tenantStage = tenant_stage_map.get(tenantStageName)
 
    deployInitContainers(tenantStage)
  }
  
  public boolean deployStation(String tenantStageName, Purpose requiredPurpose = Purpose.Service)
  {
    echo 'Deploy for ' + tenantStageName
  
    Station tenantStage = tenant_stage_map.get(tenantStageName)
    
    echo "ValidPurpose=${getDeployTo(tenantStage.environmentType)} requiredPurpose=${requiredPurpose}"
    if(getDeployTo(tenantStage.environmentType).isValidFor(requiredPurpose))
    {
      echo "OK, lets do this!"
      tenantStage.tenants.each {
        String tenant = it
        
        echo 'for environmentType=' + tenantStage.environmentType +
        ', environment=' + tenantStage.environment + ', tenant ' + tenant
        
        deployConfig(tenantStage, tenant, 'DeployConfig')

      }
        
      if(!toolsDeploy) {
        deployInitContainers(tenantStage)
      
        // this just creates task defs
        deployServiceContainers(tenantStage)
        
        // this actually deploys service containers
        fugueDeployStation(releaseTrack, tenantStage)
        
        
//        tenantStage.tenants.each {
//            String tenant = it
//            
//            echo 'for environmentType=' + tenantStage.environmentType +
//            ', environment=' + tenantStage.environment + ', tenant ' + tenant
//            
//            deployConfig(tenantStage, tenant, 'Deploy')
//    
//          }
      }
      return true
    }
    else
    {
      echo 'No access for environmentType ' + tenantStage.environmentType + ', skipped.'
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
        
        String repo = docker_repo[pullFrom_]
        
        if(repo == null)
          throw new IllegalStateException("Unknown environment type ${pullFrom_}")
        
        String localImage = ms.name + ':' + release
        String remoteImage = repo + localImage + '-' + buildQualifier_
        
        sh 'docker pull ' + remoteImage
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
        
        String pushRepo = docker_repo[environmentType]
        
        if(pushRepo == null)
          throw new IllegalStateException("Unknown environment type ${environmentType}")
        
        

        if(pullFrom_ == null)
        {
          String localImage = ms.name + ':' + release
          String remoteImage = pushRepo + localImage + '-' + buildQualifier_
          
          sh """
docker tag ${localImage} ${remoteImage}
docker push ${remoteImage}
"""
        }
        else
        {
          String pullRepo = docker_repo[pullFrom_]
          
          String baseImage = ms.name + ':' + release + '-' + buildQualifier_
          String localImage = pullRepo + baseImage
          String remoteImage = pushRepo + baseImage
          
          sh """
docker tag ${localImage} ${remoteImage}
docker push ${remoteImage}
"""
        }
      }
    }
    else
    {
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
      String remoteImage = repo + localImage + '-' + buildQualifier_
      
      sh """
docker tag ${localImage} ${remoteImage}
docker push ${remoteImage}
"""
    }
  }
  
  public void pushDockerToolImage(String environmentType, String name)
  {
    if(doBuild_ && deployTo_[environmentType])
    {
      echo 'Push Tool Image for ' + name
      
      String repo = docker_repo[environmentType]
      
      if(repo == null)
        throw new IllegalStateException("Unknown environment type ${environmentType}")
      
      String localImage = name + ':' + release
      String remoteImage = repo + name + ':latest'
      
      sh 'docker tag ' + localImage + ' ' + remoteImage
      sh 'docker push ' + remoteImage
    }
  }
  
  public void sleep(long time)
  {
    Thread.sleep(time)
  }

  public void deployConfig(Station tenantStage, String tenant, String task)
  {
    String logGroup
    String accountId = getCredentialName(tenantStage.environmentType)
    
    steps.withCredentials([[
      $class: 'AmazonWebServicesCredentialsBinding',
      accessKeyVariable: 'AWS_ACCESS_KEY_ID',
      credentialsId: accountId,
      secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']])
    {
      logGroup = createLogGroup('fugue')
    }
    
    FugueDeploy deploy = new FugueDeploy(this, task,
      logGroup,
      awsRegion)
        .withConfigGitRepo(configGitOrg, configGitRepo, configGitBranch)
        .withStation(tenantStage)
        .withTenantId(tenant)
        .withServiceName(servicename)
    
    if(toolsDeploy)
      deploy.withDockerLabel(':' + release + '-' + buildQualifier_)
      
    deploy.execute() 
  }
  
  public void fugueDeployStation(String releaseTrack, Station tenantStage)
  {
    String logGroup
    String accountId = getCredentialName(tenantStage.environmentType)
    
    steps.withCredentials([[
      $class: 'AmazonWebServicesCredentialsBinding',
      accessKeyVariable: 'AWS_ACCESS_KEY_ID',
      credentialsId: accountId,
      secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']])
    {
      logGroup = createLogGroup('fugue')
    }
    
    FugueDeploy deploy = new FugueDeploy(this, 'DeployStation',
      logGroup,
      awsRegion)
        .withConfigGitRepo(configGitOrg, configGitRepo, configGitBranch)
        .withTrack(releaseTrack)
        .withStation(tenantStage)
        .withServiceName(servicename)
    
    if(toolsDeploy)
      deploy.withDockerLabel(':' + release + '-' + buildQualifier_)
      
    deploy.execute()
  }
  
  String logGroupName(Station tenantStage, String tenant)
  {
    return logGroupName(tenantStage, tenant, symteam)
  }
  
  String logGroupName(Station tenantStage, String tenant, String team)
  {
    if(tenantStage.logGroupName == null) {
      String name = tenantStage.environmentType + '-' + tenantStage.environment + '-' + tenantStage.realm + '-';
      
      return tenant == null ? name + team : name + tenant + '-' + team;
    }
    else {
      return tenantStage.logGroupName;
    }
  }
  
  String createLogGroup(Station tenantStage, String tenant)
  {
    createLogGroup(logGroupName(tenantStage, tenant));
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
  
  public static def parameters(env, steps, extras = null)
  {
    steps.echo 'extras=' + extras.getClass().toString()
    
    def list = [
    steps.choice(name: 'buildAction',       choices:      Default.choice(env, 'buildAction', ['Build to Smoke Test', 'Build to QA', 'Deploy to Dev', 'Promote QA to Prod', 'Promote Dev to QA']), description: 'Action to perform'),
   
    steps.string(name: 'releaseVersion',    defaultValue: Default.value(env,  'releaseVersion',    ''),      description: 'The release version for promotion.'),
    steps.string(name: 'buildQualifier',    defaultValue: Default.value(env,  'buildQualifier',    ''),      description: 'The build qualifier for promotion.'),
    steps.string(name: 'serviceRepoOrg',    defaultValue: Default.value(env,  'serviceRepoOrg',    'SymphonyOSF'),  description: 'GitHub organization (fork) for service source code repo.'),
    steps.string(name: 'serviceRepoBranch', defaultValue: Default.value(env,  'serviceRepoBranch', 'master'),description: 'GitHub branch for service source code repo.'),
    steps.string(name: 'configRepoOrg',     defaultValue: Default.value(env,  'configRepoOrg',     'SymphonyOSF'),  description: 'GitHub organization (fork) for config repo.'),
    steps.string(name: 'configRepoBranch',  defaultValue: Default.value(env,  'configRepoBranch',  'master'), description: 'GitHub branch for config repo.')
   ]
   
   if(extras != null)
     list.addAll(extras)
     
    return [
  steps.parameters(list)
]
  }
  
  public def createRole(String accountId, String policyName, String roleName)
  {
    String policyArn = 'arn:aws:iam::' + aws_identity[accountId].Account + ':policy/' + policyName
    
    return createRoleByArn(accountId, policyArn, roleName)
  }
  
  public def createRoleByArn(String accountId, String policyArn, String roleName)
  {
    
   
    echo 'aws --region ' + awsRegion + ' iam get-role --role-name ' + roleName
    def status = sh returnStatus:true, script:'aws --region ' + awsRegion + ' iam get-role --role-name ' + roleName

    if(status==0)
    {
      echo 'Role ' + roleName + " exists."
    }
    else
    {
      echo 'Creating role ' + roleName + "..."
      
      sh 'aws --region ' + awsRegion + ' iam create-role --role-name ' + roleName +
        ' --assume-role-policy-document \'' + trust_relationship_document + '\''
        
      sh 'aws --region ' + awsRegion + ' iam attach-role-policy --role-name ' + roleName +
        ' --policy-arn ' + policyArn
      
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
