# Firebase로 호스팅할 로그 모니터 페이지

이 폴더에는 Firebase Hosting에 올릴 수 있는 **실시간 로그 확인 전용** 정적 페이지가 들어 있습니다. 기존 안드로이드 앱에서의 로컬 로그 저장은 유지하면서, Firebase Firestore에 로그를 복제해서 이 페이지에서 원격으로 확인할 수 있도록 구성합니다.

## 준비 (1회)

1. Firebase 콘솔에서 새 프로젝트를 만들고 Firestore를 활성화하세요.  
2. Hosting을 켜고 Firebase CLI를 설정(`firebase login`, `firebase init hosting`)하세요.  
3. `firebase-log-monitor/public/firebase-config.js`를 열고 Firebase 콘솔에서 받은 설정값(`apiKey`, `projectId` 등)을 채워 넣으세요.

## 로그 업로드

실시간 로그를 Firestore에서도 보고 싶다면, 안드로이드 앱에서 로그가 생성될 때 Firestore 컬렉션(`autoTestLogs`)에 동일한 항목을 기록해야 합니다. 예:

```kotlin
val db = Firebase.firestore
db.collection("autoTestLogs").document(logId.toString())
    .set(mapOf(
        "packageName" to packageName,
        "startTime" to System.currentTimeMillis(),
        "status" to "running",
        "touchCount" to 0
    ))
```

완료/실패 시 `status`, `endTime`, `touchCount` 필드를 업데이트하세요.

필요하다면 Firebase Authentication 없이도 Cloud Firestore 규칙을 `allow read, write: if true;`로 설정하고 테스트하세요. 실제 서비스에서는 인증/보호를 강화하세요.

## 배포

```bash
cd firebase-log-monitor
firebase deploy --only hosting
```

## 기능

- 상단: 전체/오늘/이번 주 실행 통계 (Firestore Aggregation이 없는 경우 클라이언트에서 단순 카운트).  
- 아래 리스트: 최근 로그 20개 표시, 상태 칩, 터치 횟수, 시작/종료 시간.  
- 새로고침/초기화 버튼으로 Firestore에서 다시 읽기.

필요한 경우 `firebase.json`의 `rewrites`나 `headers`를 수정하고, `public/index.html`을 확장해 UI를 커스터마이징하세요.

## Android Auto Upload Setup

The Android service now pushes each test lifecycle into Firestore. Fill `android/app/src/main/assets/firebase_config.json` with your Firebase `projectId` and `apiKey` so they match the values in `firebase-log-monitor/public/firebase-config.js`. The app will then write (and update) documents inside the `autoTestLogs` collection through the REST API, letting the hosted log page immediately show every run.
