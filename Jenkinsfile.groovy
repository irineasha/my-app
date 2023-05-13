pipeline {
    agent any
    stages {
        stage('SCM_Checkout') {
            steps {
                checkout scmGit(branches: [[name: '*/irineproject']], extensions: [], userRemoteConfigs: [[credentialsId: 'github_authentication', url: 'https://github.com/irineasha/my-app.git']])
            }
        }
        stage('SonarQube Analysis') {
            steps {
              script {
             withSonarQubeEnv(credentialsId: 'sonarqube_token') {
                 
              sh 'mvn clean verify sonar:sonar' 
             }
            }
            
            }
        }
        
        stage("Quality Gate") {
            steps {
                sleep(60)
              timeout(time: 1, unit: 'HOURS') {
                waitForQualityGate abortPipeline: true, credentialsId: 'sonarqube_token'
              }
            }
            post {
        
        failure {
            echo 'sending email notification from jenkins'
            
                   step([$class: 'Mailer',
                   notifyEveryUnstableBuild: true,
                   recipients: emailextrecipients([[$class: 'CulpritsRecipientProvider'],
                                      [$class: 'RequesterRecipientProvider']])])

            
               }
            }
          }
          
        stage('Maven Compile') {
            steps {
                sh 'whoami'
                sh script: 'mvn clean install'
                sh 'mv target/myweb*.war target/newapp.war'
            }
        }
        
        stage('Build Docker Image') {
            steps {
                script {
                    
                    sh 'cd /var/lib/jenkins/workspace/shirley'
                    def dockerImage = docker.build("irineasha/shirleyapp:latest", "--file Dockerfile .")
                }
            }
        }
      
        
        stage('Push Image to ECR') {
            steps {
                script {
                    docker.withRegistry(
                        'https://066924190412.dkr.ecr.us-east-1.amazonaws.com','ecr:us-east-1:aws_credentials') {
                            def myImage = docker.build('eks_project')
                            myImage.push('latest')
                        }
                    }
                }
        }
        
        stage('Approval - Deploy EKS'){
            steps {
                
                input 'Approve for EKS Deploy'
            }
        }
        
        stage('EKS Deploy') {
            steps {
                
                echo 'Deploying on EKS'
                withKubeCredentials(kubectlCredentials: [[caCertificate: '', clusterName: '', contextName: '', credentialsId: 'k88s', namespace: '', serverUrl: '']]) {
               
                 sh 'kubectl apply -f /var/lib/jenkins/tomcat-deployment.yaml'
             }
            }
        }
    }
}



