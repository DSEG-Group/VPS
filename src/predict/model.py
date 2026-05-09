# import torch
# import torch.nn as nn
# from torch.nn import Linear, ReLU, Sequential

# class value_predict_model(nn.Module):
#     def __init__(self,
#                  type_dim=5,
#                  param_dim=51,
#                  context_dim=4,
#                  hidden_dim=64,
#                  embed_dim=32,
#                  drop_p=0.5):
#         super().__init__()

#         self.type_dim = type_dim
#         self.type_embedding = nn.Embedding(type_dim, embed_dim)

#         # # type 本身仍然编码
#         # self.type_mlp = Sequential(
#         #     Linear(type_dim, hidden_dim),
#         #     ReLU(),
#         #     Linear(hidden_dim, hidden_dim)
#         # )

#         # context 编码（共享）
#         self.context_mlp = Sequential(
#             Linear(context_dim, hidden_dim),
#             ReLU(),
#             nn.Dropout(drop_p),
#             Linear(hidden_dim, hidden_dim),
#             ReLU(),
#             nn.Dropout(drop_p)
#         )

#         # ===== 核心修改点 =====
#         # 每种 type 一个 param_mlp，但结构完全一样
#         # self.param_mlps = nn.ModuleList([
#         #     Sequential(
#         #         Linear(param_dim, hidden_dim),
#         #         ReLU(),
#         #         Linear(hidden_dim, hidden_dim)
#         #     )
#         #     for _ in range(type_dim)
#         # ])

#         self.param_encoder = nn.Sequential(
#             nn.Linear(param_dim + embed_dim, hidden_dim),
#             nn.ReLU(),
#             nn.Dropout(p=drop_p),   # Dropout 增强正则化
#             nn.Linear(hidden_dim, hidden_dim),
#             nn.ReLU(),
#             nn.Dropout(p=drop_p),
#         )

#         self.value_predict = Sequential(
#             Linear(hidden_dim * 2+embed_dim, hidden_dim*2),
#             ReLU(),
#             nn.Dropout(drop_p),
#             Linear(hidden_dim *2,hidden_dim),
#             ReLU(),
#             nn.Dropout(drop_p),
#             Linear(hidden_dim, 1)
#         )

#     def forward(self, type_em, params_em, context_em):
#         """
#         type_em:    [B, type_dim]   (one-hot)
#         params_em:  [B, param_dim]
#         context_em: [B, context_dim]
#         """

#         type_idx = torch.argmax(type_em, dim=1)          # [B]
#         type_vec = self.type_embedding(type_idx)         # [B, embed_dim]

#         # ——— params + type embedding 连接做联合特征 ———
#         params_in = torch.cat([params_em, type_vec], dim=1)
#         params_feat = self.param_encoder(params_in)      # [B, hidden_dim]

#         # ——— 上下文编码 ———
#         context_feat = self.context_mlp(context_em)  # [B, hidden_dim]

#         # ——— type embedding 也可以作为特征参与融合 ———
#         type_feat = type_vec                             # [B, embed_dim]

#         # ——— 统一融合所有特征 ———
#         fused = torch.cat([type_feat, params_feat, context_feat], dim=1)

#         # ——— 最终预测 ———
#         out = self.value_predict(fused)                  # [B, 1]
#         return out.squeeze(-1)

import torch
import torch.nn as nn

class TransactionValueModel(nn.Module):
    def __init__(self,
                 type_dim=5,            # 事务类别数
                 embed_dim=32,          # Embedding 维度
                 param_dim=6,           # w_id,d_id,c_id
                 item_dim=15,        # i_id, i_quantity
                 context_dim=4,         # 上下文特征
                 hidden_dim=64,
                 item_hidden=32,
                 drop_rate=0.5):
        super().__init__()

        # —— 事务类型 Embedding —— #
        self.type_emb = nn.Embedding(type_dim, embed_dim)

        # —— 参数编码器 —— #
        self.param_encoder = nn.Sequential(
            nn.Linear(param_dim, hidden_dim),
            nn.Dropout(drop_rate),
            nn.ReLU(),
            nn.Linear(hidden_dim, hidden_dim),
            nn.Dropout(drop_rate),
            nn.ReLU(),
        )

        # —— 序列编码 —— #
        self.items_encoder = nn.Sequential(
            nn.Linear(item_dim,item_hidden),
            nn.Dropout(drop_rate),
            nn.ReLU(),
            nn.Linear(item_hidden,item_dim),
            nn.Dropout(drop_rate),
            nn.ReLU()
        )

        # —— 上下文编码器 —— #
        self.context_encoder = nn.Sequential(
            nn.Linear(context_dim, hidden_dim),
            nn.Dropout(drop_rate),
            nn.ReLU(),
            nn.Linear(hidden_dim, hidden_dim),
            nn.Dropout(drop_rate),
            nn.ReLU(),
        )

        # —— 融合 + 回归 head —— #
        # 拼接: type_emb + param_feat + seq_feat + context_feat
        fused_dim = embed_dim + hidden_dim*2 +1
        self.predictor = nn.Sequential(
            nn.Linear(fused_dim, hidden_dim),
            nn.Dropout(drop_rate),
            nn.ReLU(),
            nn.Linear(hidden_dim, hidden_dim // 2),
            nn.Dropout(drop_rate),
            nn.ReLU(),
            nn.Linear(hidden_dim // 2, 1)     # 回归输出
        )

    def forward(self, type_idx, params, context, items_id,items_quantity):
        """
        type_idx:   LongTensor [B]          # 事务类型标签
        params:     FloatTensor [B, 3]       # 参数
        seq_items:  FloatTensor [B, T, 2]    # new_order 序列
        context:    FloatTensor [B, 4]       # 上下文
        """

        # —— 类型 Embedding —— #
        type_index = torch.argmax(type_idx, dim=-1)
        type_vec = self.type_emb(type_index)  # [B, embed_dim]

        # —— 参数编码 —— #
        p_feat = self.param_encoder(params)  # [B, hidden_dim]

        # —— 序列编码（Packed LSTM） —— #

        i_feat = self.items_encoder(items_id)
        order_feat = i_feat.sum(dim=1, keepdim=True)

        # —— 上下文编码 —— #
        ctx_feat = self.context_encoder(context)  # [B, hidden_dim]

        # —— 融合 —— #
        fused = torch.cat([type_vec, p_feat, order_feat, ctx_feat], dim=1)
        out = self.predictor(fused).squeeze(1)    # [B]

        return out
