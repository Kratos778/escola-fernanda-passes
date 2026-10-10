# Escola Fernanda — Passes e Propinas

## Estrutura

```
android/          → APK Portaria (Kotlin, compilado no GitHub Actions)
web/              → Painel HTML (Netlify / InfinityFree)
supabase/         → Schema SQL
.github/workflows → Build do APK
```

## 1. Supabase

Secrets do GitHub (Settings → Secrets → Actions):
- `SUPABASE_URL` = https://xxxx.supabase.co
- `SUPABASE_ANON_KEY` = eyJ...

Staff admin:
```sql
insert into public.staff (id, nome, papel)
select id, 'Nome', 'admin' from auth.users where email = 'SEU@EMAIL';

select public.definir_codigo_admin('SEU_CODIGO_MIN_8');
```

## 2. HTML (Netlify / InfinityFree)

Publica a pasta `web/`:
1. Abre `index.html`
2. Cola URL + anon key → Guardar
3. Define código de admin (uma vez)
4. Login com email/password do Supabase Auth
5. Criar alunos, pagamentos, consultar propinas

## 3. APK Portaria

1. Mete secrets `SUPABASE_URL` e `SUPABASE_ANON_KEY`
2. Actions → **Build APK Portaria** → Run workflow
3. Descarrega o artefacto `portaria-fernanda-apk`

Funcionalidades: login staff, modo ENTRADA/SAÍDA, QR, offline+sync.

## Segurança

- Nunca uses `service_role` no APK nem no HTML
- QR = só token do passe
