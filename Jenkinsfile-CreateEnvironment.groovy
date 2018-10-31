@Library('fugue-cicd@Bruce-2018-10-31')


import org.symphonyoss.s2.fugue.cicd.v2.FuguePipeline
import org.symphonyoss.s2.fugue.cicd.v2.FugueDeploy
import org.symphonyoss.s2.fugue.cicd.v2.CreateEnvironmentTask

properties([
  parameters([
    string(name: 'environmentType', defaultValue: 'dev',        description: 'The environment type', ),
    string(name: 'environment',     defaultValue: 's2dev1',     description: 'The environment ID', ),
    string(name: 'realm',           defaultValue: 'us1',        description: 'The realm ID', ),
    string(name: 'region',          defaultValue: 'awsUsEast1', description: 'The region ID', )
   ])
])

node
{
    FuguePipeline pipeLine = FuguePipeline.instance(env, steps)
      .withTeam('fugue')
      .withConfigGitRepo('SymphonyOSF', 'S2-fugue-config', 'master')
      .withToolsDeploy(true)
    
    stage('Preflight')
    {
      pipeLine.toolsPreFlight()
    }
    stage('Create Environment')
    { 
      pipeLine.createEnvironmentTask(environmentType, environment, realm, region)
        .execute()
    }
}