# GitHub Secrets 등록 체크리스트

> Settings → Secrets and variables → Actions → New repository secret

## Docker Hub

| Secret | 값 | 비고 |
|--------|---|------|
| `DOCKERHUB_USERNAME` | `devjian` | Docker Hub 사용자명 |
| `DOCKERHUB_TOKEN` | Docker Hub Access Token | Read & Write 권한 |

## EC2 접속

| Secret | 값 | 비고 |
|--------|---|------|
| `EC2_HOST` | `3.34.132.7` | EC2 퍼블릭 IP |
| `EC2_USER` | `ec2-user` | SSH 유저 |
| `EC2_SSH_KEY` | PEM 키 내용 전체 | `-----BEGIN` ~ `-----END` 포함 |

## DB 접속정보

| Secret | 값 | 비고 |
|--------|---|------|
| `DB_URL` | `jdbc:postgresql://RDS엔드포인트:5432/triagain` | application-prod.yml의 ${DB_URL} |
| `DB_USERNAME` | PostgreSQL 사용자명 | |
| `DB_PASSWORD` | PostgreSQL 비밀번호 | |

## 앱 시크릿

| Secret | 값 | 비고 |
|--------|---|------|
| `JWT_SECRET` | JWT 서명 키 | Base64, 256bit 이상 |
| `INTERNAL_API_KEY` | Lambda /internal API 인증 키 | |

## AWS (Lambda 배포용)

| Secret | 값 | 비고 |
|--------|---|------| 
| `AWS_ACCESS_KEY_ID` | IAM 자격증명 | SAM deploy용 |
| `AWS_SECRET_ACCESS_KEY` | IAM 자격증명 | SAM deploy용 |
| `BACKEND_URL` | `http://3.34.132.7:8080` | Lambda → Backend 호출 URL |
