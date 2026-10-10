# Sistema de Passes e Gestão de Propinas — Escola Fernanda

Sistema integrado para gerir alunos, passes escolares, propinas, entradas e saídas.

## Componentes

| Componente | Descrição |
|------------|-----------|
| **Painel Web** | Administração no PC: alunos, passes, pagamentos, relatórios, auditoria |
| **APK Portaria** | Leitura de QR Codes, registo de entradas/saídas, modo offline |
| **Supabase** | Base de dados central (PostgreSQL), autenticação, RLS, Realtime |
| **App Alunos** (opcional) | Passe digital e consulta do estado da propina |

## Ficheiros neste repositório

```
├── README.md
├── docs/
│   └── requisitos.md          # Documento completo de requisitos
├── supabase/
│   └── schema.sql             # Esquema completo + funções + RLS
└── web/
    └── passe-fernanda.html    # Gerador de carteiras / passes (impressão)
```

## Código de administração

No ficheiro `web/passe-fernanda.html`:

1. Abra o HTML no **Chrome**.
2. Na secção **Código de administração**, defina um código (mín. 8 caracteres) **uma única vez**.
3. O código fica guardado no `localStorage` do navegador e permanece fixo.
4. Pode visualizá-lo ou apagá-lo localmente se necessário.

No servidor (Supabase), o código de administração é definido **uma vez** pela função `definir_codigo_admin` (hash bcrypt). Nunca deve ser colocado em texto simples no HTML ou no APK.

## Arranque rápido (Supabase)

1. Crie um projeto Supabase novo.
2. SQL Editor → cole `supabase/schema.sql` → Run.
3. Authentication → Users → adicione o seu utilizador (email + palavra-passe).
4. No SQL Editor, torne-o administrador:

```sql
insert into public.staff (id, nome, papel)
select id, 'Nome', 'admin' from auth.users where email = 'SEU@EMAIL';
```

5. Defina o código de administração **pelo painel** (função `definir_codigo_admin`), não pelo SQL Editor.

## Regras principais

- QR Code contém **apenas** o token aleatório do passe (nunca dados pessoais).
- Cada pagamento renova **30 dias** (configurável).
- A mesma referência de transação **nunca** renova duas vezes.
- Entradas offline ficam **pendentes de autorização** da administração.
- Saídas podem ser registadas offline.
- Toda a escrita passa por funções com validação no servidor (RLS ativo).

## Segurança

- Nunca coloque a chave `service_role` no HTML público ou no APK.
- Use apenas as chaves de cliente (anon / authenticated).
- Teste todas as permissões antes de usar dados reais.

## Licença

Uso interno — Escola Fernanda.
