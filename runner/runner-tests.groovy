timeout(60) {
    node("maven-slave") {
        wrap([$class: 'BuildUser']) {
            currentBuild.description = """
                User : $BUILD_USER
                Branch : $BRANCH
            """

            config = readYaml text: env.YAML_CONFIG ?: null

            //чтение yaml конфига если он есть
            if (config != null) {
                for (param in config.entrySet()) {
                    env.setProperty(param.getKey(), param.getValue())
                }
            }
            //получение списка типов теста (гет проперти прочитает как строку)
            testType = env.getProperty('TEST_TYPES').replaceAll("\\[", "").replace("]", "").split(",\\s*")
        }

        def jobs = [:]
        def triggerJobs = [:]

        //объекты джоб
        try {
            for (type in testType) {
                jobs[type] = {
                    node("maven-slave") {
                        sh "jobs"
                        stage("Running $type") {
                            triggerJobs[type] = build(job: "$type", parameters: [
                                    text(name: 'YAML_CONFIG', value: env.YAML_CONFIG)
                            ])
                        }
                    }
                }
            }
            parallel jobs
        } finally {
            environmentsCreate()
            copyAllureReport()
        }

        //формирование environments.txt - это файл, в котором рисуется environment (переменные окружения)
//        stage("Create additional allure report artifacts") { //environment в отчете
//            dir("allure-results") {
//                sh "echo BASE_URL=${env.getProperty('BASE_URL')} > enviroments.txt"
//                sh "echo BROWSER=${env.getProperty('BROWSER')} >> enviroments.txt"
//                sh "echo VERSION_BROWSER=${env.getProperty('VERSION_BROWSER')} >> enviroments.txt"
//            }
//        }

        //копирование артефактов, selector - выборка джобы - получение последней выполненной,
        //optional - если не найдет артефакт, то стейдж не зафейлит
//        stage("Copy allure reports") {
//            dir("allure-results") {
//                for (type in testType) {
//                    sh(pwd)
//                    copyArtifacts filter: "allure-report.zip", projectName: "${triggerJobs[type].projectName}", selector: lastSuccessful(), optional: true
//                    sh(ls -a)
//                    sh "unzip ./allure-report.zip -d ."
//                    sh "rm -rf ./allure-report.zip"
//                }
//            }
//        }

        //публикация отчета для всех прогов
        stage("Publish allure reports") {
            allure([
                    includeProperties: false,
                    jdk              : '',
                    reportBuildPolicy: 'ALWAYS',
                    results          : [[path: 'target/allure-results']]
            ])
        }
    }
}

def environmentsCreate() {
    //формирование environments.txt - это файл, в котором рисуется environment (переменные окружения)
    stage("Create additional allure report artifacts") { //environment в отчете
        dir("allure-results") {
            sh "echo BASE_URL=${env.getProperty('BASE_URL')} > enviroments.txt"
            sh "echo BROWSER=${env.getProperty('BROWSER')} >> enviroments.txt"
            sh "echo VERSION_BROWSER=${env.getProperty('VERSION_BROWSER')} >> enviroments.txt"
        }
    }
}

def copyAllureReport() {
    stage("Copy allure reports") {
        dir("allure-results") {
            for (type in testType) {
                sh "pwd"
                copyArtifacts filter: "allure-report.zip", projectName: "${triggerJobs[type].projectName}", selector: lastSuccessful(), optional: true
                sh "ls -a"
                sh "unzip ./allure-report.zip -d ."
                sh "rm -rf ./allure-report.zip"
            }
        }
    }
}