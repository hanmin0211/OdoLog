// 회사 서버(플릿 관리): 앱과 따로 빌드되는 독립 프로젝트. 실행: ..\gradlew -p server run
rootProject.name = "odolog-server"

dependencyResolutionManagement {
    repositories { mavenCentral() }
}
