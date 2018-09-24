package org.symphonyoss.s2.fugue.cicd.v1

import org.apache.commons.lang3.text.StrSubstitutor;
import org.jenkinsci.plugins.workflow.cps.DSL
import org.jenkinsci.plugins.workflow.cps.EnvActionImpl

import java.util.Map.Entry

class FuguePipeline implements Serializable
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
  
  private FuguePipeline(EnvActionImpl env, DSL steps)
  {
   // super(env, steps)
    
      this.environ = env
      this.steps = steps
      
      steps.echo 'T1'
      this.steps.echo 'T2'
      
//      steps_.echo 'T3'
      
      echo 'HERE I AM!'
      //new JenkinsTask(env, steps).execute()
  }

  public static FuguePipeline instance(EnvActionImpl env, DSL steps)
  {
    return new FuguePipeline(env, steps)
  }
  
  public void echo(String message)
  {
    steps."echo" message
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
    
    String pwd = steps.sh(script: "pwd", returnStdout: true).toString().trim()

    
    File dir = new File("$pwd/config/environment");
    
    dir.listFiles().each
    {
      File environmentType -> 
        def config = steps.readJSON file: environmentType.absolutePath + '/environmentType.json'
        environmentTypeConfig[environmentType.name] = new EnvironmentTypeConfig(steps, config."amazon")
    }
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
    new FuguePipelineTask(environ, steps, this).execute()
    
    throw new IllegalStateException("STOP")
    
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
      awsRegion)
        .withConfigGitRepo(configGitOrg, configGitRepo, configGitBranch)
        .withStation(tenantStage)
        .withTenantId(tenant)
        .withServiceName(servicename)
    
    if(toolsDeploy)
      deploy.withDockerLabel(':' + release + '-' + buildNumber)
      
    deploy.execute() 
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
