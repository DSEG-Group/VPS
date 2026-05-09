import pandas as pd
import numpy as np

# =========================
# 1. TPC-C C_LAST 规则表
# =========================

C_LAST_TOKENS = [
    "BAR", "OUGHT", "ABLE", "PRI", "PRES",
    "ESE", "ANTI", "CALLY", "ATION", "EING"
]

TOKEN2ID = {tok: i for i, tok in enumerate(C_LAST_TOKENS)}

# =========================
# 2. c_last 反编码
# =========================

def encode_c_last(c_last: str):
    ids = []
    i = 0
    if c_last == '0' or c_last == 'null':
        ids = [-1,-1,-1]
    else:
        while i < len(c_last):
            matched = False
            for tok, tid in TOKEN2ID.items():
                if c_last.startswith(tok, i):
                    ids.append(tid)
                    i += len(tok)
                    matched = True
                    break
            if not matched:
                raise ValueError(f"非法 c_last 字符串: {c_last}")

    if len(ids) != 3:
        raise ValueError(f"c_last token 数量不为 3: {c_last}")

    return ids

# =========================
# 3. input 解析 + padding
# =========================

TARGET_INPUT_LEN = 36   # 34 - 1 + 3

def parse_input_to_list(input_str: str):
    parts = input_str.split(",")
    if len(parts) > 34:
        raise ValueError(f"input 长度错误: {len(parts)}")

    inputdata = []

    for i, x in enumerate(parts):
        if i == 3:
            inputdata.extend(encode_c_last(x))
        else:
            inputdata.append(float(x))

    # 末尾补 -1
    if len(inputdata) < TARGET_INPUT_LEN:
        inputdata.extend([-1] * (TARGET_INPUT_LEN - len(inputdata)))
    elif len(inputdata) > TARGET_INPUT_LEN:
        raise ValueError(f"inputdata 长度超出: {len(inputdata)}")

    return inputdata

# =========================
# 4. context 解析 + padding
# =========================

TARGET_CONTEXT_LEN = 4   # 根据你示例 "0,0.0,0,0.0"

def parse_context_to_list(context_str: str):
    parts = context_str.split(",")

    context = [float(x) for x in parts]

    # 末尾补 0
    if len(context) < TARGET_CONTEXT_LEN:
        context.extend([0.0] * (TARGET_CONTEXT_LEN - len(context)))
    elif len(context) > TARGET_CONTEXT_LEN:
        raise ValueError(f"context 长度超出: {len(context)}")

    return context

# =========================
# 5. transType → one-hot
# =========================

TRANS_TYPE_MAP = {
    "New-Order": 0,
    "Payment": 1,
    "Order-Status": 2,
    "Delivery": 3,
    "Stock-Level": 4
}

NUM_TYPES = len(TRANS_TYPE_MAP)

def type_to_onehot(trans_type: str):
    onehot = [0] * NUM_TYPES
    onehot[TRANS_TYPE_MAP[trans_type]] = 1
    return onehot

# =========================
# 6. CSV 加载主流程
# =========================

def load_csv(csv_path):
    df = pd.read_csv(csv_path).fillna('0')

    features = {
        "inputdatas":[],
        "items_quantity":[],
        "items_id":[],
        "contexts":[],
        "values":[],
        "type_onehots":[]
    }


    for _, row in df.iterrows():
        inputdata = parse_input_to_list(row["input"])[0:6]
        items = parse_input_to_list(row["input"])[6:]
        context = parse_context_to_list(row["context"])
        type_oh = type_to_onehot(row["transType"])

        items_id = []
        items_quantity = []
        for i in range(items.__len__()):
            if i % 2 == 0:
                if items[i] == -1:
                    items_id.append(0)
                else:
                    items_id.append(items[i])
            else:
                if items[i] == -1:
                    items_quantity.append(0)
                else:
                    items_quantity.append(items[i])


        features["inputdatas"].append(inputdata)
        features["contexts"].append(context)
        features["values"].append(float(row["value"]))
        features["type_onehots"].append(type_oh)
        features["items_id"].append(items_id)
        features["items_quantity"].append(items_quantity)

    return features

# =========================
# 7. 运行入口
# =========================

if __name__ == "__main__":
    csv_path = "/SSD00/lyb/test/VPS/output.csv"  # ← 修改为你的 CSV 路径

    features = load_csv(csv_path)

    print("样本数:", len(features["type_onehots"]))
    print("inputdata 长度:", len(features["inputdatas"][3]))        # 51
    print("context 长度:", len(features["contexts"][3]))            # 4
    print("c_last token:", features["inputdatas"][3][3:6])
    print("context:", features["contexts"][3])
    print("type one-hot:", features["type_onehots"][3])
    print("value:", features["values"][3])
