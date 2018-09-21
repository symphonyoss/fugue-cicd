@Library('fugue-cicd')


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
    
    stage('Preflight')
    {
      pipeLine.sh 'pwd'
      pipeLine.sh 'ls -l'
      
      pipeLine.verifyCreds('dev')
    }
    stage('Create EnvironmentType')
    { 
      new CreateEnvironmentTypeTask(steps, pipeLine, environmentType)
        .execute()
    }
}