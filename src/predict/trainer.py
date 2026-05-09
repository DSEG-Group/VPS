import torch
from loadcsv import load_csv
from my_dataloader import build_dataloader
from model import TransactionValueModel
import random
from tqdm import tqdm
import numpy as np
import plt

def normalize_dict_arrays(data_dict,dataset_type):
    norm_dict = {}
    stats = {}  # 保存每个 key 的 (mean, std)
    for key, vals in data_dict.items():
        if(key == "type_onehots"):
            norm_dict[key] = vals
            continue

        arr_np = np.array(vals, dtype=np.float32)
        mean = arr_np.mean(axis=0)
        std  = arr_np.std(axis=0)
        if(key == "values"):
            if std == 0:
                std = 1
        else:
            std[std == 0] = 1.0
        if(key == "values" and dataset_type == 1):
            norm_dict[key] = arr_np.tolist()
        else:
            norm_arr = (arr_np - mean) / std
            norm_dict[key] = norm_arr.tolist()
            stats[key] = (mean, std)

    return norm_dict, stats

def train(epochs=300, lr=1e-5, valid_ratio=0.2, batch_size=256):
    device = "cuda" if torch.cuda.is_available() else "cpu"
    device = torch.device(device)

    # ———— 1️⃣ 加载数据（字典） ————
    features = load_csv("output.csv")
    # 假设 features 是 dict，例如 {"type_onehots": [...], "params": [...], ...}

    # 统计总样本数（字典每个 key 的长度应该一致）
    total_len = len(features["type_onehots"])

    # 生成打乱后的索引列表
    indices = list(range(total_len))
    random.shuffle(indices)

    # 计算训练/验证集大小
    valid_len = int(total_len * valid_ratio)
    train_len = total_len - valid_len

    train_idx = indices[:train_len]
    valid_idx = indices[train_len:]

    # ———— 2️⃣ 构造训练/验证集 dict ————
    def subset_features(all_feats, idxs):
        """根据 idxs 取子集，返回新的 dict"""
        return {
            key: [all_feats[key][i] for i in idxs]
            for key in all_feats
        }

    train_feats = subset_features(features, train_idx)
    valid_feats = subset_features(features, valid_idx)

    # # 归一化
    # train_norm,train_stats = normalize_dict_arrays(train_feats,0)
    # valid_norm,valid_stats = normalize_dict_arrays(valid_feats,1)

    # # ———— 3️⃣ DataLoader 构造 ————
    # train_loader = build_dataloader(train_norm, batch_size=batch_size)
    # valid_loader = build_dataloader(valid_norm, batch_size=batch_size)

    # 不归一化进行DataLoader 构造
    train_loader = build_dataloader(train_feats, batch_size=batch_size)
    valid_loader = build_dataloader(train_feats, batch_size=batch_size)


    # ———— 4️⃣ 模型、优化器、损失函数 ————
    model = TransactionValueModel().to(device)
    optimizer = torch.optim.Adam(model.parameters(), lr)
    loss_fn = torch.nn.L1Loss()

    best_valid_loss = float("inf")
    best_valid_diff = float("inf")
    train_losses = []
    valid_losses =[]
    valid_diffs = []

    save_epoch = 0
    for epoch in range(epochs):
        # ———— 训练阶段 ————
        model.train()
        train_loss_accum = 0.0
        train_batches = 0
        

        for type_em, params_em, context_em, value, items_id, items_quantity in tqdm(train_loader):
            type_em = type_em.to(device)
            params_em = params_em.to(device)
            context_em = context_em.to(device)
            value = value.to(device)
            items_id = items_id.to(device)
            items_quantity = items_quantity.to(device)

            

            pred = model(type_em, params_em, context_em,items_id,items_quantity)
            loss = loss_fn(pred, value)

            optimizer.zero_grad()
            loss.backward()
            optimizer.step()

            train_loss_accum += loss.item()
            train_batches += 1


        avg_train_loss = train_loss_accum / train_batches if train_batches > 0 else 0.0
        train_losses.append(avg_train_loss)

        # ———— 验证阶段 ————
        model.eval()
        valid_loss_accum = 0.0
        valid_batches = 0
        valid_diff_mean = 0.0

        with torch.no_grad():
            for type_em, params_em, context_em, value, items_id, items_quantity in valid_loader:
                type_em = type_em.to(device)
                params_em = params_em.to(device)
                context_em = context_em.to(device)
                value = value.to(device)
                items_id = items_id.to(device)
                items_quantity = items_quantity.to(device)


                pred = model(type_em, params_em, context_em,items_id,items_quantity)
                
                valid_loss_accum += loss.item()
                valid_batches += 1
                # #反归一化
                # pred_orgi = pred*train_stats["values"][1]+train_stats["values"][0]
                # # valid_orgi = value*valid_stats["values"][0]+valid_stats["values"][1]
                # diff_rate = torch.where(value == 0,
                #                         torch.abs(pred_orgi - value),
                #                         torch.abs(pred_orgi - value)/value
                #                         )
                # loss = loss_fn(pred_orgi, value)
                
                # 不进行归一化运行
                diff_rate = torch.where(
                            value == 0,
                            torch.abs(pred - value),
                            torch.abs(pred - value) / value
                            )

                mean_diff = diff_rate.mean()
                valid_diff_mean +=mean_diff
                

        avg_valid_loss = valid_loss_accum / valid_batches if valid_batches > 0 else 0.0
        valid_losses.append(avg_valid_loss)
        avg_valid_diff = torch.abs(valid_diff_mean / valid_batches) if valid_batches>0 else 0.0
        valid_diffs.append(avg_valid_diff.item())
        print(f"Epoch {epoch+1}/{epochs} | "
              f"Train Loss: {avg_train_loss:.4f} | Valid Loss: {avg_valid_loss:.4f} | Valid_diff: {avg_valid_diff:.4f}")

        # ———— 保存最佳模型 ————
        # if avg_valid_loss < best_valid_loss:
        #     best_valid_loss = avg_valid_loss
        #     torch.save(model.state_dict(), "best_model.pth")
        #     print(f"→ Saved new best model (valid loss = {best_valid_loss:.4f})")

        if avg_valid_diff < best_valid_diff:
            best_valid_diff = avg_valid_diff
            torch.save(model.state_dict(), "best_model.pth")
            save_epoch = epoch
            print(f"→ Saved new best model (valid loss = {best_valid_diff:.4f})")

    print("Training finished!Save epoch: "+str(save_epoch))


    epochs = range(1, len(train_losses) + 1)

    plt.figure(figsize=(8, 5))
    # train_arr = train_losses.cpu().numpy()
    # valid_arr = valid_losses.cpu().numpy()
    plt.plot(epochs, train_losses, label="Train Loss")
    plt.plot(epochs, valid_losses, label="Valid Loss")
    plt.xlabel("Epoch")
    plt.ylabel("Loss")
    plt.title("Training and Validation Loss over Epochs")
    plt.legend()
    plt.grid(True)
    plt.show()
    plt.savefig("test.png")

    plt.figure(figsize=(8, 5))
    # train_arr = train_losses.cpu().numpy()
    # valid_arr = valid_losses.cpu().numpy()
    
    plt.plot(epochs, valid_diffs, label="diffs_rate")
    plt.xlabel("Epoch")
    plt.ylabel("Loss")
    plt.title("Different rate over Epochs")
    plt.legend()
    plt.grid(True)
    plt.show()
    plt.savefig("test2.png")

if __name__ == "__main__":
    train()

