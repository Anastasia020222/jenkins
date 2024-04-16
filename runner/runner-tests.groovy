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
            //получение списка типов теста
            testType = env.getProperty('TEST_TYPES').split(",\\s*")
        }

        def jobs = [:]

        //объекты джоб
        try {
            testType.each { type ->
                jobs[type] = {
                    stage("Running $type") {
                        build(job: "$type", parameters: [
                                text(name: 'YAML_CONFIG', value: env.YAML_CONFIG)
                        ])
                    }
                }
            }
            parallel jobs

        } finally {

            //формирование environments.properties - это файл, в котором рисуется environment (переменные окружения)
            stage("Create additional allure report artifacts") { //environment в отчете
                dir("allure-results") {
                    sh "echo BASE_URL=${env.getProperty('BASE_URL')} > environment.properties"
                    sh "echo BASE_API_URL=${env.getProperty('BASE_API_URL')} >> environment.properties"
                    sh "echo WIREMOCK_URL=${env.getProperty('WIREMOCK_URL')} >> environment.properties"
                    sh "echo BROWSER=${env.getProperty('BROWSER')} >> environment.properties"
                    sh "echo VERSION_BROWSER=${env.getProperty('VERSION_BROWSER')} >> environment.properties"
                }
            }

            //копирование артефактов
            stage("Copy allure reports") {
                dir("allure-results") {
                    for (type in testType) {
                        sh "cp /root/" + type.replace("-tests", '') + "-allure/* ."
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