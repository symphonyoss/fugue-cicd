@Library('fugue-cicd')


import org.symphonyoss.s2.fugue.cicd.v1.FuguePipeline
import org.symphonyoss.s2.fugue.cicd.v1.FugueDeploy
import org.symphonyoss.s2.fugue.cicd.v1.CreateEnvironmentTask

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
      new CreateEnvironmentTask(steps, pipeLine, environmentType, environment, realm, region)
        .execute()
    }
}