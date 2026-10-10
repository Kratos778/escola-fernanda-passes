# Instalação do esquema Supabase

## Opção recomendada
Cole o ficheiro completo `schema.sql` no SQL Editor do Supabase e execute.

## Opção por partes (mesma ordem)
1. `01_header_tables.sql`
2. `02_funcoes.sql`
3. `03_admin.sql`
4. `04_propinas.sql`
5. `05_leituras.sql`
6. `06_vista_seguranca.sql`

Depois:
1. Authentication → Users → criar o seu utilizador
2. Tornar admin:
```sql
insert into public.staff (id, nome, papel)
select id, 'Seu Nome', 'admin' from auth.users where email = 'SEU@EMAIL';
```
3. Definir o código de administração pelo painel (função `definir_codigo_admin`).
