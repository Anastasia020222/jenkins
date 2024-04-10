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
                for (param in config.entrySer()) {
                    env.setProperty(param.getKey(), param.getValue())
                }
            }
        }

        //получение списка типов теста (гет проперти прочитает как строку)
        testType = env.getProperty('TEST_TYPES').replaceAll("[", "").replace("]", "").split(",\\s*")
    }

    def jobs = [:]
    def triggerJobs = [:]

    //объекты джоб
    for (type in testType) {
        jobs[type] = {
            node("maven") {
                stage("Running $type tests")
                triggerJobs[type] = build(job: "$type-tests", parameters: [
                        text(name: 'YAML_CONFIG', value: env.YAML_CONFIG)
                ])
            }
        }
    }
    parallel jobs

    //формирование enviroments.txt - это файл, в котором рисуется enviroment (переменные окружения)
    stage("Create additional allure report artifacts") { //enviroment в отчете
        sh "BROWSER=${env.getProperty('BROWSER')} > enviroments.txt"
        sh "TEST_VERSION=${env.getProperty('TEST_VERSION')} > enviroments.txt"
    }

    //копирование артефактов selector - выборка джобы - получение последней выполненной, optional - если не найдет артефакт, то стейдж не зафейлит
    stage("Copy allure reports") {
        dir() {
            for (type in testType) {
                copyArtifacts filter: "allure-report.zip", projectName: "${triggerdJobs[type].projectName}", selector: lastSuccessful(), optional: true
                sh "unzip ./allure-report.zip -d ."
                sh "rm -rf ./allure-report.zip"
            }
        }
    }

    //публикация отчета для всех прогов
    stage("Publish allure reports") {
        dir("allure-results") {
            allure([
                    results          : ["."],
                    reportBuildPolicy: ALWAYS
            ])
        }
    }
}