@Library('fugue-cicd@Bruce-2018-10-11')


import org.symphonyoss.s2.fugue.cicd.v1.FuguePipeline
import org.symphonyoss.s2.fugue.cicd.v1.FugueDeploy
import org.symphonyoss.s2.fugue.cicd.v1.CreateEnvironmentTypeTask

properties([
  parameters([
    string(name: 'environmentType', defaultValue: 'dev', description: 'The environment type', )
   ])
])

node
{
    FuguePipeline pipeLine = FuguePipeline.instance(env, steps)
      .withUseRootCredentials(true)  // We need to force use of root credentials
      .withTeam('fugue')
      .withConfigGitRepo('SymphonyOSF', 'S2-fugue-config', 'master')
      .withToolsDeploy(true)
    
    stage('Preflight')
    {
      steps.sh 'aws --version'
      
      pipeLine.toolsPreFlight()
    }
    stage('Create EnvironmentType')
    { 
      pipeLine.createEnvironmentTypeTask(environmentType)
        .execute()
    }
}