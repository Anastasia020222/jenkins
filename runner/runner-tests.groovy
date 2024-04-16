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
            testType = env.getProperty('TEST_TYPES').split(",\\s*")
        }

        def jobs = [:]

        //объекты джоб
        try {
            println("testType " + testType)
            //for (type in testType) {
            testType.each {type ->
                println("type " + type)
                jobs[type] = {
                    println("jobs[type] " + jobs[type].toString())
                    println("type 2 " + type)
                    stage("Running $type") {
                        sh "env"
                        build(job: "$type", parameters: [
                                text(name: 'YAML_CONFIG', value: env.YAML_CONFIG)
                        ])
                    }
                }
            }
            parallel jobs
        } finally {

            //формирование environments.txt - это файл, в котором рисуется environment (переменные окружения)
            stage("Create additional allure report artifacts") { //environment в отчете
                dir("allure-results") {
                    sh "echo BASE_URL=${env.getProperty('BASE_URL')} > environment.properties"
                    sh "echo BASE_API_URL=${env.getProperty('BASE_API_URL')} >> environment.properties"
                    sh "echo WIREMOCK_URL=${env.getProperty('WIREMOCK_URL')} >> environment.properties"
                    sh "echo BROWSER=${env.getProperty('BROWSER')} >> environment.properties"
                    sh "echo VERSION_BROWSER=${env.getProperty('VERSION_BROWSER')} >> environment.properties"
                }
            }

            //копирование артефактов, selector - выборка джобы - получение последней выполненной,
            //optional - если не найдет артефакт, то стейдж не зафейлит
            stage("Copy allure reports") {
                dir("allure-results") {
                    for (type in testType) {

                        sh "pwd"
                        sh "cp /root/" + type.replace("-tests", '') + "-allure/* ."
//                        copyArtifacts filter: "allure-report.zip", projectName: type, selector: lastSuccessful(), optional: true
//                        sh "ls -a"
//                        sh "unzip ./allure-report.zip -d ."
//                        sh "rm -rf ./allure-report.zip"
                        sh "ls -a"
                    }
                }
            }

            //публикация отчета для всех прогонов
            stage("Publish allure reports") {
                allure([
                        results          : [[path: './allure-results']],
                        reportBuildPolicy: 'ALWAYS'
                ])
            }
        }
    }
}