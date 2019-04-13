@Library('fugue-cicd')


import org.symphonyoss.s2.fugue.cicd.v3.FuguePipeline
import org.symphonyoss.s2.fugue.cicd.v3.FugueDeploy
import org.symphonyoss.s2.fugue.cicd.v3.CreateEnvironmentTypeTask

properties([
  parameters([
    string(name: 'environmentType', defaultValue: 'dev', description: 'The environment type', )
   ])
])

node
{
    FuguePipeline pipeLine = FuguePipeline.instance(env, steps)
      .withUseRootCredentials(true)  // We need to force use of root credentials
      .withConfigGitRepo('SymphonyOSF', 'S2-fugue-config', 'master')
      .withToolsDeploy(true)
    
    stage('Preflight')
    {
      steps.sh 'aws --version'
      pipeLine.verifyCreds('dev', true)
      pipeLine.verifyCreds(environmentType, true)
      pipeLine.toolsPreFlight()
    }
    stage('Create EnvironmentType')
    { 
      pipeLine.createEnvironmentTypeTask(environmentType)
        .execute()
    }
}