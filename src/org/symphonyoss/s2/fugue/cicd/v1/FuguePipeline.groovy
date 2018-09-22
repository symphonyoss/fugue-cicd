package org.symphonyoss.s2.fugue.cicd.v1

import org.apache.commons.lang3.text.StrSubstitutor;
import java.util.Map.Entry

class FuguePipeline implements Serializable
{
  private Map tenants = [:]

  private Map aws_identity = [:]
  private Map docker_repo = [:]
  private Map<String, EnvironmentTypeConfig> environmentTypeConfig = [:]
  private Map<String, Container> service_map = [:]
  private Map<String, Station> tenant_stage_map = [:]
  private Map<String, String> role_map = [:]
  private Set<String> logGroupSet = []
  private String symbase = 'symbase'

  private def environ
  private def steps
  private String servicename
  private String symteam = 'sym'

  private String awsRegion = 'us-east-1'
  private String release
  private String buildNumber
  private String servicePath
  private boolean toolsDeploy = false;
  private boolean useRootCredentials = false;
  
  private Map<String, Boolean> envTypePush = [:]
  
  private String serviceGitOrg
  private String serviceGitRepo
  private String serviceGitBranch
  private String configGitOrg
  private String configGitRepo
  private String configGitBranch
  
  private String releaseTrack
  
  private FuguePipeline(env, steps)
  {
      this.environ = env
      this.steps = steps
  }

  public static FuguePipeline instance(env, steps) {
      // TODO: this is a place holder for future polymorphism
      FuguePipeline ms = new FuguePipeline(env,steps)
      return ms
  }
  
  public String toString() {
    StringBuilder b = new StringBuilder();
    
    b.append "{" +
      "\n  symteam              =" + symteam +
      "\n  release              =" + release +
      "\n  buildNumber          =" + buildNumber +
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
    
    s.withPipeline(this)
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
  
  public FuguePipeline withBuildNumber(String v) {
      buildNumber = v
      return this
  }
  
  public FuguePipeline withRole(String name, String file) {
    if(role_map.containsKey(name))
      throw new IllegalArgumentException('Role "' + name + '" redefined.')
      
      role_map.put(name, file)
      
      return this
  }
  
  public FuguePipeline withServiceGitRepo(String org, String repo, String branch = 'master') {
    serviceGitOrg = org
    serviceGitRepo = repo
    serviceGitBranch = branch
    return this
  }
  
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
    useRootCredentials = b
    toolsDeploy = b
    return this
  }
  
  /** Intended for fugue-tools use only NOT TO BE CALLED BY NORMAL SERVICES */
  public FuguePipeline withUseRootCredentials(boolean b) {
    useRootCredentials = b
    return this
  }
  
  public String getDockerLabel() {
    ':' + release + '-' + buildNumber
  }

  private String serviceFullName(String env, String tenant) {
      return env+'-'+tenant+'-'+servicename
  }
  
  public void deleteUser(String userName, String groupName)
  {
    def status = steps.sh returnStatus:true, script:'aws iam get-user --user-name ' + userName
    
    if(status==0)
    {
      steps.echo 'User ' + userName + " exists, deleting..."
        
      steps.sh 'aws iam remove-user-from-group --user-name ' + userName +
          ' --group-name ' + groupName
      
      def keys = steps.readJSON(text:
        steps.sh(returnStdout: true, script: 'aws iam list-access-keys --user-name ' + userName))
      
      keys."AccessKeyMetadata".each
      {
        keySpec ->
          steps.sh 'aws iam delete-access-key --access-key-id ' + keySpec."AccessKeyId" +
           ' --user-name ' + userName
      }
      
      steps.sh 'aws iam delete-user --user-name ' + userName
    }
    else
    {
      steps.echo 'User ' + userName + " does not exist."
    }
  }
  
  public CreateEnvironmentTypeTask  createEnvironmentTypeTask(String environmentType)
  {
    return new CreateEnvironmentTypeTask(steps, this, environmentType)
      .withConfigGitRepo(configGitOrg, configGitRepo, configGitBranch)
  }
  
  public CreateEnvironmentTask  createEnvironmentTask(String environmentType, String environment,
    String realm, String region)
  {
    return new CreateEnvironmentTask(steps, this, environmentType, environment, realm, region)
      .withConfigGitRepo(configGitOrg, configGitRepo, configGitBranch)
  }
  
  public void loadConfig()
  {
    steps.echo 'loadConfig'
    
    steps.echo 'git credentialsId: symphonyjenkinsauto url: https://github.com/' + configGitOrg + '/' + configGitRepo + '.git branch: ' + configGitBranch
    steps.git credentialsId: 'symphonyjenkinsauto', url: 'https://github.com/' + configGitOrg + '/' + configGitRepo + '.git', branch: configGitBranch

    steps.sh 'pwd'
    steps.sh 'ls -l config'
    steps.sh 'ls -l config/environment'
    
    String pwd = steps.sh(script: "pwd", returnStdout: true).toString().trim()
    
    
    steps.echo "pwd =" + pwd 
    
    File dir = new File("$pwd/config/environment");
    
    steps.echo "dir.absolutePath =" + dir.absolutePath
    
    steps.echo "dir.listFiles() =" + dir.listFiles()
    
    dir.listFiles().each
    {
      environmentType -> 
      def config = steps.readJSON file: environmentType + '/environmentType.json'
    
      steps.echo 'config=' + config
      
      
      def conf = new EnvironmentTypeConfig(steps, config."amazon")
      steps.echo 'T2'
      steps.echo 'conf=' + conf
      environmentTypeConfig[environmentType] = new EnvironmentTypeConfig(steps, config."amazon")
      steps.echo 'T3'
    }
    
    steps.echo 'T4'
    throw new IllegalStateException('STOP')
  }
  
  public void toolsPreFlight()
  {
    steps.sh 'rm -rf *'
    
    if(configGitRepo != null)
    {
      loadConfig()
    }
    
    verifyCreds('dev')
  }
  
  public void preflight()
  {
    steps.sh 'rm -rf *'

    if(configGitRepo != null)
    {
      loadConfig()

      steps.sh 'ls -l config/service/' + servicename + '/service.json'

      def service = steps.readJSON file:'config/service/' + servicename + '/service.json'

      steps.echo 'service is ' + service
      steps.echo 'service.containers is ' + service.containers
      steps.echo 'service."containers" is ' + service."containers"

      service."containers".each { name, containerDef ->
        steps.echo 'Container ' + name + ' = ' + containerDef


        Container ms = new Container()
            .withName(name)
            .withRole(containerDef."role")
            .withTenancy(containerDef."tenancy" == "SINGLE" ? Tenancy.SingleTenant : Tenancy.MultiTenant)
            .withContainerType(containerDef."containerType" == "INIT" ? ContainerType.Init : ContainerType.Runtime)
            .withPort(containerDef."port")
            .withEcsTemplate(containerDef."ecsTemplate")

        containerDef."environment".each { envName, envValue ->
          ms.withEnv(envName, envValue)
        }

        //steps.echo 'ms=' + ms

        this.withContainer(ms)
      }


      def track = steps.readJSON file:'config/track/' + releaseTrack + '.json'

      steps.echo 'track is ' + track
      steps.echo 'track.description is ' + track.description
      steps.echo 'track."stations" is ' + track."stations"

      track."stations".each { stationDef ->
        steps.echo 'Station ' + stationDef."name" + ' = ' + stationDef


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

        steps.echo 'ts=' + ts

        this.withStation(ts)
      }
    }
      
    steps.echo 'serviceGitOrg is ' + serviceGitOrg
    steps.echo 'serviceGitRepo is ' + serviceGitRepo
    steps.echo 'serviceGitBranch is ' + serviceGitBranch
    
    steps.git credentialsId: 'symphonyjenkinsauto', url: 'https://github.com/' + serviceGitOrg + '/' + serviceGitRepo + '.git', branch: serviceGitBranch

    verifyCreds('dev')


    try
    {
      verifyCreds('qa')
      try
      {
        verifyCreds('stage')
      }
      catch(Exception e)
      {
        steps.echo 'Exception ' + e.toString()
        steps.echo 'No Stage Access -- Cannot promote'
      }
    }
    catch(Exception e)
    {
      steps.echo 'Exception ' + e.toString()
      steps.echo 'No QA  Access -- Cannot promote'
    }
  }
  
  public void verifyUserAccess(String credentialId, String environmentType = null)
  {
    steps.echo 'Verifying ' + environmentType + ' access with credential ' + credentialId + '...'
    
    steps.withCredentials([[
      $class:             'AmazonWebServicesCredentialsBinding',
      accessKeyVariable:  'AWS_ACCESS_KEY_ID',
      credentialsId:      credentialId,
      secretKeyVariable:  'AWS_SECRET_ACCESS_KEY']])
    {
      aws_identity[credentialId] = steps.readJSON(text: steps.sh(returnStdout: true, script: 'aws sts get-caller-identity'))

      //steps.sh 'aws --region ' + awsRegion + ' ecs list-clusters'

      if(environmentType != null)
      {
//        steps.echo 'T1'
//        
//        
//        steps.echo 'TA1'
//        steps.echo 'environmentType=' + environmentType
//        steps.sh 'ls -l'
//        steps.echo 'TA2'
//        steps.sh 'ls'
//        steps.echo 'TA3'
//        steps.sh 'ls config/environment/' + environmentType
//        steps.echo 'TA4'
//        def config = steps.readJSON file:'config/environment/' + environmentType + '/environmentType.json'
//        
//        steps.echo 'config=' + config
//        
//        
//        def conf = new EnvironmentTypeConfig(steps, config."amazon")
//        steps.echo 'T2'
//        steps.echo 'conf=' + conf
//        environmentTypeConfig[environmentType] = new EnvironmentTypeConfig(steps, config."amazon")
//        steps.echo 'T3'
        docker_repo[environmentType] = aws_identity[credentialId].Account+'.dkr.ecr.us-east-1.amazonaws.com/'+symteam+'/'
        
        service_map.values().each
        {
          Container ms = it
            
          try
          {
              steps.sh 'aws --region ' + awsRegion + ' ecr describe-repositories --repository-names '+symteam+'/'+ms.name
          }
          catch(Exception e)
          {
              steps.echo 'Exception ' + e.toString()
              steps.sh 'aws --region ' + awsRegion + ' ecr create-repository --repository-name '+symteam+'/'+ms.name
          }
        }
        
        steps.sh "set +x; echo 'Logging into docker repo'; `aws --region " + awsRegion + " ecr get-login --no-include-email`"
                    
        if(environmentType=='dev')
        {
                        
          steps.sh 'docker pull 189141687483.dkr.ecr.' + awsRegion + '.amazonaws.com/symphony-es/base-java8:latest'
          
          
          steps.withCredentials([steps.file(credentialsId: 'maven-settings', variable: 'FILE')]) {
            steps.sh 'cp $FILE /usr/share/maven/conf/settings.xml'
            
          }
  
          //steps.sh 'aws s3 cp s3://sym-build-secrets/sbe/settings.xml  /usr/share/maven/conf/settings.xml'
          
          if(!toolsDeploy)
          {
            steps.sh 'docker pull ' + aws_identity[credentialId].Account+'.dkr.ecr.us-east-1.amazonaws.com/fugue/' + 'fugue-deploy:latest'
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
  
  public void verifyCreds(String environmentType)
  {
    
    
    verifyUserAccess(getCredentialName(environmentType), environmentType);
    envTypePush.put(environmentType, true);
    //steps.echo 'AWS '+environmentType+' docker repo: '+docker_repo[environmentType]
  }
  


  private def R53RecordSetExist(String environmentType, String environment, String tenant) {
      steps.withCredentials([[
        $class: 'AmazonWebServicesCredentialsBinding',
        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
        credentialsId: getCredentialName(environmentType),
        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']])
      {
        steps.echo 'R53RecordSetExist environmentType=' + environmentType +
        ', environment=' + environment + ', tenant ' + tenant
        steps.echo 'aws --region ' + awsRegion + ' route53 list-resource-record-sets --hosted-zone-id '+ECSClusterMaps.env_cluster[environment]['r53_zone']+' --query \"ResourceRecordSets[?Name == \''+tenant+'.'+ECSClusterMaps.env_cluster[environment]['dns_suffix']+'.\']\" > r53-rs-list-'+environment+'-'+tenant+'-'+servicename+'.json'
        
        steps.sh 'aws --region ' + awsRegion + ' route53 list-resource-record-sets --hosted-zone-id '+ECSClusterMaps.env_cluster[environment]['r53_zone']+' --query \"ResourceRecordSets[?Name == \''+tenant+'.'+ECSClusterMaps.env_cluster[environment]['dns_suffix']+'.\']\" > r53-rs-list-'+environment+'-'+tenant+'-'+servicename+'.json'
          def resource_record = steps.readJSON file:'r53-rs-list-'+environment+'-'+tenant+'-'+servicename+'.json'
          if(resource_record) {
              steps.echo """
R53 record set exists.
Value: ${resource_record[0].ResourceRecords[0].Value}
Name:  ${resource_record[0].Name}
"""
              return true
          } else {
              steps.echo "R53 record set does not exist."
              return false
          }
      }
  }

  private def createR53RecordSet(String environmentType, String environment, String tenant) {
      if(!R53RecordSetExist(environmentType, environment, tenant)) {
          def rs_template_args = ['SERVICE_NAME':servicename,
                                  'TENANT_ID':tenant,
                                  'DNS_SUFFIX':ECSClusterMaps.env_cluster[environment]['dns_suffix'],
                                  'ALB_DNS':ECSClusterMaps.env_cluster[environment]['ialb_dns']
          ]
          def rs_def = (new StrSubstitutor(rs_template_args)).replace(record_set_template)
          def rs_def_file = 'r53-rs-'+environment+'-'+tenant+'-'+servicename+'.json'
          steps.writeFile file:rs_def_file, text:rs_def
          //steps.sh 'ls -alh'
          steps.withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: getCredentialName(environmentType), secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
              steps.sh 'aws --region ' + awsRegion + ' route53 change-resource-record-sets --hosted-zone-id '+ECSClusterMaps.env_cluster[environment]['r53_zone']+' --change-batch file://'+rs_def_file+' > r53-rs-create-out-'+environment+'-'+tenant+'-'+servicename+'.json'
          }
      }
  }
  
  

public void deployInitContainers(Station tenantStage)
{
  steps.echo 'Init Containers'
  
  service_map.values().each {
    Container ms = it
    
    if(ms.containerType == ContainerType.Init) {
      switch(ms.tenancy)
      {
        case Tenancy.SingleTenant:
          tenantStage.tenants.each {
            String tenant = it
            
    
            steps.echo 'Init ' + tenant + ' ' + ms.toString()
            
            ms.deployInit(tenantStage, tenant)
          }
          break

        case Tenancy.MultiTenant:
          steps.echo 'Init MULTI ' + ms.toString()
          ms.deployInit(tenantStage, null)
          break
      }
    }
  }
}

public void deployServiceContainers(Station tenantStage)
{
  steps.echo 'Runtime Containers'
  
  service_map.values().each {
    Container ms = it
    
    if(ms.containerType == ContainerType.Runtime) {
      switch(ms.tenancy)
      {
        case Tenancy.SingleTenant:
          tenantStage.tenants.each {
            String tenant = it
            
            steps.echo 'Runtime ' + tenant + ' ' + ms.toString()
            ms.deployService(tenantStage, tenant)
          }
          break

        case Tenancy.MultiTenant:
          steps.echo 'Runtime MULTI ' + ms.toString()
            ms.deployService(tenantStage, null)
            break
        }
      }
    }
  }
  
  public void deployInitContainers(String tenantStageName) {
    Station tenantStage = tenant_stage_map.get(tenantStageName)
 
    deployInitContainers(tenantStage)
  }
  
  public void deployStation(String tenantStageName)
  {
    steps.echo 'Deploy for ' + tenantStageName
  
    Station tenantStage = tenant_stage_map.get(tenantStageName)
  
    
    if(envTypePush.get(tenantStage.environmentType))
    {
      steps.echo 'R53RecordSets and Roles environmentType' + tenantStage.environmentType +
        ', environment=' + tenantStage.environment + ', tenants=' + tenantStage.tenants
      
      
      tenantStage.tenants.each {
        String tenant = it
        
        steps.echo 'for environmentType=' + tenantStage.environmentType +
        ', environment=' + tenantStage.environment + ', tenant ' + tenant
        
        deployConfig(tenantStage, tenant, 'DeployConfig')

      }
        
      if(!toolsDeploy) {
        deployInitContainers(tenantStage)
      
        // this just creates task defs
        deployServiceContainers(tenantStage)
        
        // this actually deploys service containers
        tenantStage.tenants.each {
            String tenant = it
            
            steps.echo 'for environmentType=' + tenantStage.environmentType +
            ', environment=' + tenantStage.environment + ', tenant ' + tenant
            
            deployConfig(tenantStage, tenant, 'Deploy')
    
          }
      }
    }
    else
    {
      steps.echo 'No access for environmentType ' + tenantStage.environmentType + ', skipped.'
    }
  }


  public void pushDockerImages(String environmentType)
  {
    steps.echo 'Push Images for ' + environmentType
    
    if(envTypePush.get(environmentType))
    {
      service_map.values().each {
        Container ms = it
        
        String repo = docker_repo[environmentType]
        
        if(repo == null)
          throw new IllegalStateException("Unknown environment type ${environmentType}")
        
        String localImage = ms.name + ':' + release
        String remoteImage = repo + localImage + '-' + buildNumber
        
        steps.sh 'docker tag ' + localImage + ' ' + remoteImage
        steps.sh 'docker push ' + remoteImage
      }
    }
    else
    {
      steps.echo 'No access for environmentType ' + environmentType + ', skipped.'
    }
  }
  
  public void pushDockerToolCandidate(String environmentType, String name) {
    
    steps.echo 'Push Tool candidate for ' + name
    
    String repo = docker_repo[environmentType]
    
    if(repo == null)
      throw new IllegalStateException("Unknown environment type ${environmentType}")
    
    String localImage = name + ':' + release
    String remoteImage = repo + localImage + '-' + buildNumber
    
    steps.sh 'docker tag ' + localImage + ' ' + remoteImage
    steps.sh 'docker push ' + remoteImage
  }
  
  public void pushDockerToolImage(String environmentType, String name) {
    
    steps.echo 'Push Tool Image for ' + name
    
    String repo = docker_repo[environmentType]
    
    if(repo == null)
      throw new IllegalStateException("Unknown environment type ${environmentType}")
    
    String localImage = name + ':' + release
    String remoteImage = repo + name + ':latest'
    
    steps.sh 'docker tag ' + localImage + ' ' + remoteImage
    steps.sh 'docker push ' + remoteImage
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
    
    FugueDeploy deploy = new FugueDeploy(steps, this, task,
      logGroup,
      aws_identity[accountId].Account, 
      ECSClusterMaps.env_cluster[tenantStage.environment]['name'],
      awsRegion)
        .withConfigGitRepo(configGitOrg, configGitRepo, configGitBranch)
        .withStation(tenantStage)
        .withTenantId(tenant)
        .withServiceName(servicename)
    
    if(toolsDeploy)
      deploy.withDockerLabel(release + '-' + buildNumber)
      
    deploy.execute() 
      
      
      
      
//    String taskDefFamily = 'fugue-deploy-' + tenantStage.environmentType + '-' + tenantStage.environment
//    String deployConfigVersion = toolsDeploy ? release + '-' + buildNumber : 'latest'
//    def template_args =
//    [
//      'AWSREGION':awsRegion,
//      'FUGUE_ENVIRONMENT_TYPE':tenantStage.environmentType,
//      'FUGUE_ENVIRONMENT':tenantStage.environment,
//      'FUGUE_REALM':tenantStage.realm,
//      'FUGUE_REGION':tenantStage.region,
//      'FUGUE_SERVICE':servicename,
//      'FUGUE_PRIMARY_ENVIRONMENT':tenantStage.primaryEnvironment,
//      'FUGUE_PRIMARY_REGION':tenantStage.primaryRegion,
//      'TASK_ROLE_ARN':'arn:aws:iam::' + aws_identity[tenantStage.environmentType].Account + ':role/' + 
//        tenantStage.environmentType + '-' + tenantStage.environment + '-admin-role',
//      'LOG_GROUP':'',
//      'FUGUE_TENANT':tenant,
//      'FUGUE_ACTION':task,
//      'MEMORY':1024,
//      'TASK_FAMILY':taskDefFamily,
//      'SERVICE_IMAGE':aws_identity[tenantStage.environmentType].Account+'.dkr.ecr.us-east-1.amazonaws.com/fugue/fugue-deploy:' + deployConfigVersion,
//      'CONSUL_TOKEN':'',
//      'GITHUB_TOKEN':'',
//      'GITHUB_ORG':configGitOrg,
//      'GITHUB_REPO':configGitRepo,
//      'GITHUB_BRANCH':configGitBranch
//    ]
//    
//    steps.withCredentials([steps.string(credentialsId: 'sym-consul-'+tenantStage.environmentType, variable: 'CONSUL_TOKEN')])
//    {
//      // TODO: CONSUL_TOKEN needs to be moved
//      template_args['CONSUL_TOKEN'] = steps.sh(returnStdout:true, script:'echo -n $CONSUL_TOKEN').trim()
//    }
//      
//    steps.withCredentials([steps.string(credentialsId: 'symphonyjenkinsauto-token', variable: 'GITHUB_TOKEN')])
//    {
//      template_args['GITHUB_TOKEN'] = steps.sh(returnStdout:true, script:'echo -n $GITHUB_TOKEN').trim()
//    }
//    
//    steps.withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'sym-aws-'+tenantStage.environmentType, secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']])
//    {
//
//      template_args['LOG_GROUP'] = createLogGroup('fugue')
//      
//      //def taskdef = (new groovy.text.SimpleTemplateEngine()).createTemplate(taskdef_template).make(template_args).toString()
//      def taskdef = (new org.apache.commons.lang3.text.StrSubstitutor(template_args)).replace(config_taskdef_template)
//      //steps.echo taskdef
//      def taskdef_file = 'ecs-'+tenantStage.environment+'-'+tenant+'.json'
//      steps.writeFile file:taskdef_file, text:taskdef
//      
////          steps.echo 'taskdef file is ' + taskdef_file
//      steps.echo 'Vconf1 taskdef is ' + taskdef
//      steps.echo 'Vconf1 tenantStage.primaryEnvironment is ' + tenantStage.primaryEnvironment
//      steps.echo 'Vconf1 template_args is ' + template_args
//    
//      steps.sh 'aws --region ' + awsRegion + ' ecs register-task-definition --cli-input-json file://'+taskdef_file+' > ecs-taskdef-out-'+tenantStage.environment+'-'+tenant+'.json'
////          def tdefout = steps.readJSON file: 'ecs-taskdef-out-'+tenantStage.environment+'-'+tenant+'.json'
////          tenants[tenantStage.environment][tenant]['ecs-taskdef-arn'] = tdefout.taskDefinition.taskDefinitionArn
////          tenants[tenantStage.environment][tenant]['ecs-taskdef-family'] = tdefout.taskDefinition.family
////          tenants[tenantStage.environment][tenant]['ecs-taskdef-revision'] = tdefout.taskDefinition.revision
////          steps.echo """
////ECS Task Definition ARN:      ${tenants[tenantStage.environment][tenant]['ecs-taskdef-arn']}
////ECS Task Definition Family:   ${tenants[tenantStage.environment][tenant]['ecs-taskdef-family']}
////ECS Task Definition Revision: ${tenants[tenantStage.environment][tenant]['ecs-taskdef-revision']}
////"""
//   
//      def taskRun = steps.readJSON(text:
//        steps.sh(returnStdout: true, script: 'aws --region ' + awsRegion + ' ecs run-task --cluster ' + ECSClusterMaps.env_cluster[tenantStage.environment]['name'] +
//        ' --task-definition fugue-deploy --count 1'
//        )
//      )
//        
//      steps.echo """
//Task run
//taskArn: ${taskRun.tasks[0].taskArn}
//lastStatus: ${taskRun.tasks[0].lastStatus}
//"""
//      String taskArn = taskRun.tasks[0].taskArn
//      String taskId = taskArn.substring(taskArn.lastIndexOf('/')+1)
//      
//      steps.sh 'aws --region ' + awsRegion + ' ecs wait tasks-stopped --cluster ' + ECSClusterMaps.env_cluster[tenantStage.environment]['name'] +
//        ' --tasks ' + taskArn
//      
//
//      def taskDescription = steps.readJSON(text:
//        steps.sh(returnStdout: true, script: 'aws --region ' + awsRegion + ' ecs describe-tasks  --cluster ' + 
//          ECSClusterMaps.env_cluster[tenantStage.environment]['name'] +
//          ' --tasks ' + taskArn
//        )
//      )
//      
//      steps.echo """
//Task run
//taskArn: ${taskDescription.tasks[0].taskArn}
//lastStatus: ${taskDescription.tasks[0].lastStatus}
//stoppedReason: ${taskDescription.tasks[0].stoppedReason}
//exitCode: ${taskDescription.tasks[0].containers[0].exitCode}
//"""
//      //TODO: only print log if failed...
//      steps.sh 'aws --region ' + awsRegion + ' logs get-log-events --log-group-name fugue' +
//      ' --log-stream-name ' + taskDefFamily + '/' + taskDefFamily + '/' + taskId + ' | fgrep "message" | sed -e \'s/ *"message": "//\' | sed -e \'s/",$//\' | sed -e \'s/\\\\t/      /\''
//      if(taskDescription.tasks[0].containers[0].exitCode != 0) {
//        
//        throw new IllegalStateException('Init task fugue-deploy failed with exit code ' + taskDescription.tasks[0].containers[0].exitCode)
//      }
//    }
  }
  
  public void pushDockerImage(String env, String localimage) {
      String remoteimage = docker_repo[env]+servicename+':'+dockerlabel
      steps.sh 'docker tag '+localimage+' '+remoteimage
      steps.sh 'docker push '+remoteimage
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
      steps.echo 'Create log group ' + name + ' if necessary'
 
        def logDesc = steps.readJSON(text: 
          steps.sh(returnStdout: true, script: 'aws --region ' + awsRegion + ' logs describe-log-groups --log-group-name '+ name)
      )
      
      if(logDesc.'logGroups'.size() ==0)
      {
        steps.echo 'Create log group ' + name
        steps.sh 'aws --region ' + awsRegion + ' logs create-log-group --log-group-name '+ name
        steps.sh 'aws --region ' + awsRegion + ' logs put-retention-policy --log-group-name '+ name +' --retention-in-days 14'
      }
      else
      {
        steps.echo 'Log group ' + name + ' size=' + logDesc.'logGroups'.size()
      }
    }
    else
    {
      steps.echo 'Log group ' + name + ' already created.'
    }
    
    return name
  }

  private static final String record_set_template =
      '''{
    "Comment": "DNS for ${TENANT_ID} during ${SERVICE_NAME} service",
    "Changes": [
      {
        "Action": "UPSERT",
        "ResourceRecordSet": {
          "Name": "${TENANT_ID}.${DNS_SUFFIX}.",
          "Type": "CNAME",
          "TTL": 300,
          "ResourceRecords": [
            {
              "Value": "${ALB_DNS}"
            }
          ]
        }
      }
    ]
  }'''
      
  public def createRole(String accountId, String policyName, String roleName)
  {
    String policyArn = 'arn:aws:iam::' + aws_identity[accountId].Account + ':policy/' + policyName
   
    steps.echo 'aws --region ' + awsRegion + ' iam get-role --role-name ' + roleName
    def status = steps.sh returnStatus:true, script:'aws --region ' + awsRegion + ' iam get-role --role-name ' + roleName

    if(status==0)
    {
      steps.echo 'Role ' + roleName + " exists."
    }
    else
    {
      steps.echo 'Creating role ' + roleName + "..."
      
      steps.sh 'aws --region ' + awsRegion + ' iam create-role --role-name ' + roleName +
        ' --assume-role-policy-document \'' + trust_relationship_document + '\''
        
      steps.sh 'aws --region ' + awsRegion + ' iam attach-role-policy --role-name ' + roleName +
        ' --policy-arn ' + policyArn
      
      steps.echo 'Waiting for 20 seconds for the new role to become active....'
      Thread.sleep(20000)
      steps.echo 'OK'
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
  
//  private static final String config_taskdef_template =
//  '''{
//    "taskRoleArn": "${TASK_ROLE_ARN}",
//    "family": "fugue-deploy",
//    "containerDefinitions": [
//        {
//            "name": "${TASK_FAMILY}",
//            "image": "${SERVICE_IMAGE}",
//            "memory": ${MEMORY},
//            "essential": true,
//            "logConfiguration": {
//                "logDriver": "awslogs",
//                "options": {
//                    "awslogs-group": "${LOG_GROUP}",
//                    "awslogs-region": "${AWSREGION}",
//                    "awslogs-stream-prefix": "${TASK_FAMILY}"
//                }
//            },
//            "environment": [
//                {
//                    "name": "FUGUE_ENVIRONMENT_TYPE",
//                    "value": "${FUGUE_ENVIRONMENT_TYPE}"
//                },
//                {
//                    "name": "FUGUE_ENVIRONMENT",
//                    "value": "${FUGUE_ENVIRONMENT}"
//                },
//                {
//                    "name": "FUGUE_REALM",
//                    "value": "${FUGUE_REALM}"
//                },
//                {
//                    "name": "FUGUE_REGION",
//                    "value": "${FUGUE_REGION}"
//                },
//                {
//                    "name": "FUGUE_SERVICE",
//                    "value": "${FUGUE_SERVICE}"
//                },
//                {
//                    "name": "FUGUE_ACTION",
//                    "value": "${FUGUE_ACTION}"
//                },
//                {
//                    "name": "FUGUE_TENANT",
//                    "value": "${FUGUE_TENANT}"
//                },
//                {
//                    "name": "FUGUE_PRIMARY_ENVIRONMENT",
//                    "value": "${FUGUE_PRIMARY_ENVIRONMENT}"
//                },
//                {
//                    "name": "FUGUE_PRIMARY_REGION",
//                    "value": "${FUGUE_PRIMARY_REGION}"
//                },
//                
//                
//                {
//                    "name": "CONSUL_URL",
//                    "value": "https://consul-${FUGUE_ENVIRONMENT_TYPE}.symphony.com:8080"
//                },
//                {
//                    "name": "CONSUL_TOKEN",
//                    "value": "${CONSUL_TOKEN}"
//                },
//                {
//                    "name": "GITHUB_ORG",
//                    "value": "${GITHUB_ORG}"
//                },
//                {
//                    "name": "GITHUB_REPO",
//                    "value": "${GITHUB_REPO}"
//                },
//                {
//                    "name": "GITHUB_BRANCH",
//                    "value": "${GITHUB_BRANCH}"
//                },
//                {
//                    "name": "GITHUB_TOKEN",
//                    "value": "${GITHUB_TOKEN}"
//                },
//                {
//                    "name": "AWS_REGION",
//                    "value": "${AWSREGION}"
//                }
//            ]
//        }
//    ]
//}'''
}
