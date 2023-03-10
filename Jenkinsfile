pipeline {
  agent {
    node {
      label 'maven'
    }
  }

  parameters {
    string(name:'PROJECT_VERSION',defaultValue: 'v0.0Beta',description:'项目版本')
    string(name:'PROJECT_NAME',defaultValue: 'gmall-gateway',description:'构建模块')
  }

  environment {
    DOCKER_CREDENTIAL_ID = 'dockerhub-id'
    GITEE_CREDENTIAL_ID = 'gitee-id'
    KUBECONFIG_CREDENTIAL_ID = 'demo-kubeconfig'
    REGISTRY = 'docker.io'
    DOCKERHUB_NAMESPACE = 'wanzenghui'
    GITEE_ACCOUNT = 'lemon_wan'
    SONAR_CREDENTIAL_ID = 'sonar-qube'
    BRANCH_NAME = 'master'
  }

  stages {
    stage ('拉取代码，编译、打包') {
      steps {
        git(url: 'https://gitee.com/lemon_wan/gmall.git', credentialsId: 'gitee-id', branch: 'master', changelog: true, poll: false)
        sh 'echo 正在构建 $PROJECT_NAME  版本号：$PROJECT_VERSION 将会提交给 $REGISTRY 镜像仓库'
        container ('maven') {
          sh 'mvn clean install -Dmaven.test.skip=true -gs `pwd`/mvn-settings.xml'
        }
      }
    }

    stage('代码质量分析') {
      steps {
        container ('maven') {
          withCredentials([string(credentialsId: "$SONAR_CREDENTIAL_ID", variable: 'SONAR_TOKEN')]) {
            withSonarQubeEnv('sonar') {
              sh "echo 当前目录 `pwd`"
              sh "mvn sonar:sonar -gs `pwd`/mvn-settings.xml -Dsonar.branch=$BRANCH_NAME -Dsonar.login=$SONAR_TOKEN"
            }
          }
          timeout(time: 1, unit: 'HOURS') {
            waitForQualityGate abortPipeline: true
          }
        }
      }
    }

    stage ('构建镜像 & 推送快照镜像') {
      steps {
        container ('maven') {
          sh 'mvn -Dmaven.test.skip=true -gs `pwd`/mvn-settings.xml clean package'
          sh 'cd $PROJECT_NAME && docker build -f Dockerfile -t $REGISTRY/$ALIYUNHUB_NAMESPACE/$PROJECT_NAME:SNAPSHOT-$BRANCH_NAME-$BUILD_NUMBER .'
          withCredentials([usernamePassword(passwordVariable : 'DOCKER_PASSWORD' ,usernameVariable : 'DOCKER_USERNAME' ,credentialsId : "$DOCKER_CREDENTIAL_ID" ,)]) {
            sh 'echo "$DOCKER_PASSWORD" | docker login $REGISTRY -u "$DOCKER_USERNAME" --password-stdin'
            sh 'docker push  $REGISTRY/$ALIYUNHUB_NAMESPACE/$PROJECT_NAME:SNAPSHOT-$BRANCH_NAME-$BUILD_NUMBER'
          }
        }
      }
    }

    stage('推送最新镜像'){
      when{
        branch 'master'
      }
      steps{
        container ('maven') {
          sh 'docker tag  $REGISTRY/$ALIYUNHUB_NAMESPACE/$PROJECT_NAME:SNAPSHOT-$BRANCH_NAME-$BUILD_NUMBER $REGISTRY/$ALIYUNHUB_NAMESPACE/$PROJECT_NAME:latest '
          sh 'docker push  $REGISTRY/$ALIYUNHUB_NAMESPACE/$PROJECT_NAME:latest '
        }
      }
    }

    stage('部署到开发环境') {
      when{
        branch 'master'
      }
      steps {
        input(id: "deploy-to-dev-$PROJECT_NAME", message: "是否将$PROJECT_NAME部署到开发环境?")
        kubernetesDeploy(configs: "$PROJECT_NAME/deploy/**", enableConfigSubstitution: true,
        kubeconfigId: "$KUBECONFIG_CREDENTIAL_ID")
      }
    }

    stage('发布版本'){
      when{
        expression{
          return params.PROJECT_VERSION =~ /v.*/
        }
      }
      steps {
        container ('maven') {
          input(id: 'release-image-with-tag', message: '是否发布当前版本?')
          sh 'docker tag  $REGISTRY/$ALIYUNHUB_NAMESPACE/$PROJECT_NAME:SNAPSHOT-$BRANCH_NAME-$BUILD_NUMBER $REGISTRY/$ALIYUNHUB_NAMESPACE/$PROJECT_NAME:$PROJECT_VERSION '
          sh 'docker push  $REGISTRY/$ALIYUNHUB_NAMESPACE/$PROJECT_NAME:$TAG_NAME '
          withCredentials([usernamePassword(credentialsId: "$GITEE_CREDENTIAL_ID",
          passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
            sh 'git config --global user.email "lemon_wan@aliyun.com" '
            sh 'git config --global user.name "lemon_wan" '
            sh 'git tag -a $PROJECT_NAME-$PROJECT_VERSION -m "$PROJECT_VERSION" '
            sh 'git push http://$GIT_USERNAME:$GIT_PASSWORD@gitee.com/$GITEE_ACCOUNT/gmall.git --tags --ipv4'
          }
        }
      }
    }

  }
}