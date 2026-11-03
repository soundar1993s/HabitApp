pipeline {
  agent { label 'habitapp-linux' }
  triggers { pollSCM('H/5 * * * *') }
  options {
    timestamps()
    disableConcurrentBuilds()
    skipDefaultCheckout(true)
    timeout(time: 45, unit: 'MINUTES')
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }
  environment {
    AWS_DEFAULT_REGION = 'ap-south-1'
    IMAGE_REPOSITORY = '985344363717.dkr.ecr.ap-south-1.amazonaws.com/habitapp'
    DOCKER_CONFIG = "${WORKSPACE}/.docker-ci"
  }
  stages {
    stage('Checkout') {
      steps {
        script {
          def scmInfo = checkout scm
          env.TRUSTED_MAIN = (!env.CHANGE_ID && scmInfo.GIT_BRANCH in ['main', 'origin/main']).toString()
          env.IMAGE_TAG = "${env.BUILD_NUMBER}-${sh(script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim()}"
          echo "Image version ${env.IMAGE_TAG}; trusted main ${env.TRUSTED_MAIN}"
        }
        sh 'mkdir -p reports'
      }
    }
    stage('Maven Build') {
      steps { sh 'mvn -B -ntp clean compile' }
    }
    stage('Unit Test and Package') {
      steps { sh 'mvn -B -ntp verify' }
      post { always { junit 'target/surefire-reports/*.xml' } }
    }
    stage('SonarQube Analysis') {
      steps {
        withSonarQubeEnv(installationName: 'sonarqube-server', credentialsId: 'habitapp-sonar-token') {
          sh '''
            set +x
            export SONAR_TOKEN="$SONAR_AUTH_TOKEN"
            mvn -B -ntp sonar:sonar -Dsonar.host.url="$SONAR_HOST_URL"
          '''
        }
      }
    }
    stage('Quality Gate') {
      steps {
        timeout(time: 10, unit: 'MINUTES') { waitForQualityGate abortPipeline: true }
      }
    }
    stage('Docker Build') {
      steps { sh 'docker build --pull -t "$IMAGE_REPOSITORY:$IMAGE_TAG" .' }
    }
    stage('Validate Helm') {
      steps {
        sh '''
          helm lint helm/habitapp
          helm template habitapp helm/habitapp --set-string image.repository="$IMAGE_REPOSITORY" --set-string image.tag="$IMAGE_TAG" > reports/rendered.yaml
        '''
      }
    }
    stage('Push Image') {
      when { expression { env.TRUSTED_MAIN == 'true' } }
      steps {
        withCredentials([usernamePassword(credentialsId: 'habitapp-aws-ci', usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
          sh 'node ci/publish-image.mjs'
        }
      }
    }
    stage('Update Helm Configuration') {
      when { expression { env.TRUSTED_MAIN == 'true' } }
      steps {
        sh '''
          set -eu
          git fetch origin main
          git checkout -B gitops-update origin/main
          node ci/update-image.mjs
          git diff -- helm/habitapp/values.yaml
        '''
      }
    }
    stage('Commit Deployment Change') {
      when { expression { env.TRUSTED_MAIN == 'true' } }
      steps {
        withCredentials([sshUserPrivateKey(credentialsId: 'habitapp-git', keyFileVariable: 'GIT_SSH_KEY')]) {
          sh '''
            set -eu
            set +x
            git config user.name 'Jenkins CI'
            git config user.email 'jenkins@localhost'
            git add helm/habitapp/values.yaml
            if git diff --cached --quiet; then exit 0; fi
            git commit -m "Deploy HabitApp $IMAGE_TAG [skip ci]"
            export GIT_SSH_COMMAND="ssh -i '$GIT_SSH_KEY' -oIdentitiesOnly=yes -oStrictHostKeyChecking=yes -oUserKnownHostsFile='$WORKSPACE/ci/github_known_hosts'"
            git push git@github.com:soundar1993s/HabitApp.git HEAD:main
            git rev-parse HEAD > reports/gitops-commit.txt
            echo "$IMAGE_REPOSITORY:$IMAGE_TAG" > reports/published-image.txt
          '''
        }
      }
    }
  }
  post {
    always {
      archiveArtifacts artifacts: 'target/*.jar,target/site/jacoco/**,reports/**', allowEmptyArchive: true
      sh 'rm -rf .docker-ci'
    }
  }
}

