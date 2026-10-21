# 手机端 App 构建镜像：复用 TV 项目的构建基础镜像，补装 Android 36 平台
FROM docker-build:latest

RUN yes | sdkmanager --licenses >/dev/null 2>&1 || true \
    && sdkmanager "platforms;android-36" "build-tools;36.0.0" \
    && rm -rf /root/.android/cache
