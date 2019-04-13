@Library('fugue-cicd')


import org.symphonyoss.s2.fugue.cicd.v3.FuguePipeline
import org.symphonyoss.s2.fugue.cicd.v3.FugueDeploy
import org.symphonyoss.s2.fugue.cicd.v3.CreateEnvironmentTask

properties([
  parameters([
    string(name: 'environmentType', defaultValue: 'dev',        description: 'The environment type', ),
    string(name: 'environment',     defaultValue: 's2dev1',     description: 'The environment ID', ),
    string(name: 'region',          defaultValue: 'us-east-1',  description: 'The region ID', )
   ])
])

node
{
    FuguePipeline pipeLine = FuguePipeline.instance(env, steps)
      .withConfigGitRepo('SymphonyOSF', 'S2-fugue-config', 'master')
      .withToolsDeploy(true)
    
    stage('Preflight')
    {
      pipeLine.verifyCreds('dev', true)
      pipeLine.verifyCreds(environmentType, true)
      pipeLine.toolsPreFlight()
    }
    stage('Create Environment')
    { 
      pipeLine.createEnvironmentTask(environmentType, environment, region)
        .execute()
    }
}