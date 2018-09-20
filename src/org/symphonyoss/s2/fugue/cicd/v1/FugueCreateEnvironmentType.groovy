package org.symphonyoss.s2.fugue.cicd.v1

import java.util.Map.Entry

class FugueCreateEnvironmentType implements Serializable
{
  private def environ_
  private def steps_
  private String environmentType_
  
  private def awsIdentity_
  
  public FugueCreateEnvironmentType(env, steps, environmentType)
  {
      environ_ = env
      steps_ = steps
      environmentType_ = environmentType
  }
  
  public void preFlight()
  {
    steps_.echo 'environmentType is ' + environmentType_
    
    
    
    steps_.withCredentials([
    [
      $class: 'AmazonWebServicesCredentialsBinding',
      accessKeyVariable: 'AWS_ACCESS_KEY_ID',
      credentialsId: 'fugue-devops-'+environmentType_,
      secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
    ]])
    {
      steps_.sh 'aws sts get-caller-identity'
      
      awsIdentity_ = pipeLine.steps.readJSON(text:
        steps_.sh(returnStdout: true, script: 'aws sts get-caller-identity'
        )
      )
      
      steps_.echo 'aws account=' + awsIdentity_."Account"
    }
  }
  
  public void createEnvironmentTypeCicdUser()
  {
    def policy = (new org.apache.commons.lang3.text.StrSubstitutor(template_args)).replace(config_taskdef_template)

    
    steps_.withCredentials([
    [
      $class: 'AmazonWebServicesCredentialsBinding',
      accessKeyVariable: 'AWS_ACCESS_KEY_ID',
      credentialsId: 'fugue-devops-'+environmentType_,
      secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
    ]])
    {
      steps_.sh 'aws sts get-caller-identity'
    }
  }
  
  private static final String envTypeCicdPolicy =
  '''{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "VisualEditor0",
            "Effect": "Allow",
            "Action": [
                "ecs:SubmitTaskStateChange",
                "iam:GetRole",
                "iam:GetPolicy",
                "iam:PassRole",
                "ecs:StartTask",
                "ecs:DescribeClusters",
                "s3:GetObject",
                "ecs:SubmitContainerStateChange",
                "ecs:ListContainerInstances",
                "ecs:DescribeContainerInstances"
            ],
            "Resource": [
                "arn:aws:ecs:*:*:task-definition/${environmentType}-*",
                "arn:aws:ecs:*:*:container-instance/*",
                "arn:aws:ecs:*:*:task/${environmentType}-*",
                "arn:aws:ecs:*:*:cluster/*",
                "arn:aws:s3:::${awsConfigBucket}/config/${environmentType}-*",
                "arn:aws:s3:::sym-build-secrets/*",
                "arn:aws:iam::*:policy/${environmentType}-*",
                "arn:aws:iam::*:role/${environmentType}-*"
            ]
        },
        {
            "Sid": "VisualEditor1",
            "Effect": "Allow",
            "Action": [
                "ecs:ListServices",
                "ecs:UpdateService",
                "ecs:CreateService",
                "ecs:ListTaskDefinitionFamilies",
                "ecs:DeleteService",
                "ecs:DescribeServices",
                "ecs:DescribeTaskDefinition",
                "ecs:ListTaskDefinitions",
                "ecs:ListClusters",
                "ecs:RegisterTaskDefinition",
                "ecs:RunTask",
                "ecs:ListTasks",
                "ecs:StopTask",
                "ecs:DescribeTasks"
            ],
            "Resource": "*"
        },
        {
            "Sid": "ContainerRegistryAll",
            "Effect": "Allow",
            "Action": [
                "ecr:*"
            ],
            "Resource": "*"
        },
        {
            "Sid": "Route53DotIsymDotIoDot",
            "Effect": "Allow",
            "Action": [
                "route53:ListResourceRecordSets",
                "route53:ChangeResourceRecordSets"
            ],
            "Resource": [
                "arn:aws:route53:::hostedzone/*"
            ]
        },
        {
            "Action": [
                "logs:CreateLogGroup",
                "logs:DescribeLogGroups",
                "logs:PutRetentionPolicy",
                "logs:FilterLogEvents",
                "logs:DescribeLogStreams",
                "logs:PutRetentionPolicy",
                "logs:GetLogEvents"
            ],
            "Effect": "Allow",
            "Resource": "*",
            "Sid": "Logs"
        }
    ]
  }'''
}