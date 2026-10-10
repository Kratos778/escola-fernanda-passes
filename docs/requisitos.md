SISTEMA DE PASSES E GESTÃO DE PROPINAS
ESCOLA FERNANDA
Documento de requisitos, arquitetura, segurança e implementação
Versão: 1.0

==================================================

1. OBJETIVO DO SISTEMA
   ==================================================

Criar um sistema integrado para gerir os alunos, os passes escolares, as propinas, as entradas e saídas dos alunos e as atividades dos funcionários.

O sistema será composto por:

1. Painel Web para administração no computador.
2. Aplicação Android (APK) para leitura dos QR Codes e registo de movimentos.
3. Base de dados central no Supabase.
4. Aplicação Android opcional para os alunos consultarem o seu passe e estado das propinas.

Todas as partes devem utilizar a mesma base de dados central.

O computador da secretaria não precisa de ficar ligado permanentemente para o sistema funcionar, pois a base de dados ficará alojada no Supabase.

==================================================
2. PAINEL WEB DE ADMINISTRAÇÃO

O painel Web será utilizado pela direção e pela secretaria.

Funcionalidades:

- Criar, consultar e editar alunos.
- Registar curso, turma e outros dados necessários.
- Gerar passes escolares.
- Gerar QR Codes únicos e seguros.
- Imprimir passes.
- Bloquear passes perdidos.
- Cancelar passes antigos.
- Emitir novos passes.
- Consultar o estado das propinas.
- Registar pagamentos manuais.
- Consultar o histórico de pagamentos.
- Anular pagamentos, mediante autorização e justificação.
- Consultar entradas e saídas em tempo real, quando houver ligação.
- Consultar o histórico de movimentos.
- Gerir funcionários e permissões.
- Consultar relatórios.
- Consultar o registo de auditoria.
- Configurar os parâmetros do sistema.

O painel Web não necessita de utilizar a câmara para ler QR Codes, porque essa função será principalmente realizada pelo APK da portaria.

==================================================
3. APLICAÇÃO ANDROID DA PORTARIA

A aplicação Android será utilizada pelos funcionários autorizados a controlar as entradas e saídas.

Funcionalidades:

- Autenticação dos funcionários.
- Leitura de QR Codes através da câmara.
- Consulta do estado do passe.
- Consulta da situação das propinas.
- Registo de entradas.
- Registo de saídas.
- Histórico de movimentos.
- Indicação clara de movimentos permitidos e recusados.
- Identificação de passes bloqueados ou cancelados.
- Deteção de leituras repetidas.
- Indicação do estado da ligação à Internet.
- Armazenamento local de eventos quando estiver offline.
- Sincronização dos eventos pendentes quando a ligação regressar.
- Identificação do funcionário e dispositivo responsáveis por cada movimento.

O funcionário deverá escolher explicitamente o modo de operação:

[ ENTRADA ] [ SAÍDA ]

O sistema não deve alternar automaticamente entre entrada e saída após cada leitura, porque isso pode provocar erros.

A aplicação deve mostrar mensagens claras, como:

- ENTRADA AUTORIZADA
- ENTRADA RECUSADA: PASSE BLOQUEADO
- ENTRADA RECUSADA: PROPINA VENCIDA
- SAÍDA REGISTADA
- QR CODE DESCONHECIDO
- EVENTO GUARDADO OFFLINE
- VERIFICAÇÃO PENDENTE

Uma mensagem de evento guardado offline não pode ser apresentada como se a autorização do servidor já tivesse sido confirmada.

==================================================
4. APLICAÇÃO OPCIONAL DOS ALUNOS

Poderá existir uma segunda aplicação Android destinada aos alunos.

Funcionalidades possíveis:

- Iniciar sessão.
- Consultar o próprio passe digital.
- Apresentar o QR Code do passe.
- Consultar a situação das propinas.
- Consultar a data de vencimento.
- Consultar o histórico de pagamentos.
- Consultar notificações relevantes.

Restrições:

- O aluno só poderá consultar os seus próprios dados.
- Não poderá criar passes.
- Não poderá alterar a situação das propinas.
- Não poderá confirmar pagamentos.
- Não poderá modificar entradas e saídas.
- Não poderá consultar dados privados de outros alunos.

Esta aplicação é opcional e poderá ser desenvolvida depois do sistema principal.

==================================================
5. BASE DE DADOS CENTRAL

A plataforma escolhida será o Supabase, utilizando PostgreSQL, autenticação, políticas de segurança e funções no servidor.

Tabelas principais:

1. config
2. staff
3. alunos
4. passes
5. pagamentos
6. movimentos
7. auditoria
8. codigo_falhas

O esquema SQL existente deverá ser auditado antes da implementação final.

==================================================
6–18. (ver documento completo no repositório)

FIM DO DOCUMENTO
