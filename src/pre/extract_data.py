import re
import csv

input_file = "/SSD00/lyb/test/VPS/run/pg_result_2026-02-01_15_33_34/data/result.json"

records = []

with open(input_file, "r", encoding="utf-8") as f:
    content = f.read()

# 每条记录以 { ... } 为单位（允许换行）
blocks = re.findall(r'\{.*?\}', content, re.S)

for block in blocks:
    transType = re.search(r'"transType"\s*:\s*"([^"]*)"', block)
    value     = re.search(r'"value"\s*:\s*"([^"]*)"', block)
    input_    = re.search(r'"input"\s*:\s*"([^"]*)"', block)
    context   = re.search(r'"context"\s*:\s*"([^"]*)"', block)

    if all([transType, value, input_, context]):
        records.append({
            "transType": transType.group(1),
            "value": value.group(1),
            "input": input_.group(1),
            "context": context.group(1)
        })

# 输出结果
for i, r in enumerate(records, 1):
    print(f"Record {i}: {r}")



with open("output.csv", "w", newline="", encoding="utf-8") as f:
    writer = csv.DictWriter(
        f,
        fieldnames=["transType", "value", "input", "context"]
    )
    writer.writeheader()
    writer.writerows(records)
