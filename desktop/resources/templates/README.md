# 对账单模板

- `statement-golden-sample.xlsx`：金样板（「一恒科—鸿晟达」对帐单，业务签字版式），
  是后台对账单导出（`admin-backend` 的 `KdStatementAppServiceImpl` / `StatementXlsxWriter`）
  的唯一版式基准，也是其保真度回归测试的对照文件，不要删除或改版式。
- 一期导出引擎在无精修模板时会生成结构对齐的可读表格；有模板后改为填数以贴近签字版式。
  （本地对账单页处于双写验证期，正式导出以后台为准。）
