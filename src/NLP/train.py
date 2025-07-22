import json
import os
os.environ['HF_ENDPOINT'] = 'https://hf-mirror.com'

from transformers import RobertaTokenizerFast,RobertaModel
from sklearn.model_selection import train_test_split
from sklearn.ensemble import GradientBoostingRegressor
from sklearn.metrics import mean_squared_error,mean_absolute_error
from sklearn.neural_network import MLPRegressor
import numpy as np
import torch
from sklearn.preprocessing import StandardScaler
from tqdm import tqdm
import random
import time
from contrastiveLearn_model import ContrastiveLearn,SupConLoss,SQLDataset,TripletLoss
from torch.utils.data import TensorDataset, DataLoader
import lightgbm as lgb
from sklearn.metrics import classification_report
import matplotlib.pyplot as plt
from sklearn.manifold import TSNE
from transformers import AdamW

class Config:
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    bert_model = "bert-base-uncased"
    projection_dim = 64
    temperature = 0.1
    batch_size = 128  # 增大批次提升负样本数量
    num_epochs = 100
    num_classes = 5
    intra_class_weight = 0.3  # 类内聚类损失权重
    patience = 15

config = Config()
tokenizer:RobertaTokenizerFast  = RobertaTokenizerFast.from_pretrained("FacebookAI/xlm-roberta-base",cache_dir='/SSD/00/encoder_models/')
roberta_model = RobertaModel.from_pretrained("FacebookAI/xlm-roberta-base",cache_dir='/SSD/00/encoder_models/').to(config.device)
random.seed(42)

NEW_ORDER = 0
PAYMENT = 1
STOCK_LEVEL = 2
ORDER_STATUS = 3
DELIVERY = 4

def read_file_to_json(file_path = './run/standard_data/pg_result.json')->list:
    strline = ""
    firstEleven = ""
    json_list:list = list()
    print("reading file pg_result.json")
    with open('./run/standard_data/pg_result_32.json','r')as f:
        for line in f:
            line =  line.strip()
            if(line.__len__()>=11):
                firstEleven = line[0:11]
            else:
                firstEleven = line
            strline+=line
            if(firstEleven == "\"transType\""):
                line = strline[:-1]
                index = line.find(":")
                line = line[index+1:]
                json_str = json.loads(line)
                json_list.append(json_str)
                strline = ""
                firstEleven = ""
    f.close()
    return json_list

def sql2vec(json_list:list):
    special_tokens = ["[FOR UPDATE]","[INSERT INTO]","[DELETE FROM]"]
    tokenizer.add_special_tokens({"additional_special_tokens":special_tokens})
    roberta_model.resize_token_embeddings(len(tokenizer))
    sql_list:list = []
    value_list:list = []
    transaction_type_list:list = []#transaction_type_list{0:New-Order,1:Payment,2:Stock-Level,3:Order-Status,4:Delivery}
    print("sql2vec\n")
    temp_list = json_list.copy()
    if len(json_list)>10000:
        json_list = random.sample(temp_list,k=10000)
    
    pbar = tqdm(json_list, desc="Processing")
    
    for transactions_json in pbar:
        sql_str:str = transactions_json["sql"]
        value_list.append(float(transactions_json["value"]))
        tmp_transaction_type:str = transactions_json["transType"]
        
        if tmp_transaction_type == "New-Order":
            transaction_type_list.append(NEW_ORDER)
        elif tmp_transaction_type == "Payment":
            transaction_type_list.append(PAYMENT)
        elif tmp_transaction_type == "Stock-Level":
            transaction_type_list.append(STOCK_LEVEL)
        elif tmp_transaction_type == "Order-Status":
            transaction_type_list.append(ORDER_STATUS)
        else:
            transaction_type_list.append(DELIVERY)          
            
        sql_list.append(sql_str)
    
    return sql_list,value_list,transaction_type_list
        

def ContrastiveLearn_learn(model,train_dataloader,val_dataloader,optimizer,config):
    train_losses = []
    valid_losses = []
    
    criterion = SupConLoss()
    # criterion = TripletLoss()
    model.train()
    best_valid_loss = float('inf')
    for epoch in range(config.num_epochs):
        all_embeddings = []
        all_labels = []
        total_train_loss = 0
        for batch in tqdm(train_dataloader):
            input_ids = batch['input_ids'].to(config.device)
            attention_mask = batch['attention_mask'].to(config.device)
            labels = batch['labels'].to(config.device)
            
            # possitive_input_ids = batch['positive_input_id'].to(config.device)
            # possitive_attention_mask = batch['positive_attention_mask'].to(config.device)
            
            # negative_input_id = batch['negative_input_id'].to(config.device)
            # negative_attention_mask = batch['negative_attention_mask'].to(config.device)
            
            embeddings = model(input_ids,attention_mask)
            # po_em = model(possitive_input_ids,possitive_attention_mask)
            # ne_em = model(negative_input_id,negative_attention_mask)
            
            all_embeddings.append(embeddings.cpu())
            all_labels.append(labels.cpu())

            loss = criterion(embeddings, labels)
        
            optimizer.zero_grad()
            loss.backward()
            optimizer.step()

            total_train_loss += loss.item()
        
        #print(f"Epoch {epoch+1}/{config.num_epochs} | Loss: {total_train_loss/len(train_dataloader):.4f}")
    
        model.eval()
        total_valid_loss = 0.0
        
        with torch.no_grad():
            for batch in val_dataloader:
                input_ids = batch['input_ids'].to(config.device)
                attention_mask = batch['attention_mask'].to(config.device)
                labels = batch['labels'].to(config.device)
                
                # possitive_input_ids = batch['positive_input_id'].to(config.device)
                # possitive_attention_mask = batch['positive_attention_mask'].to(config.device)
            
                # negative_input_id = batch['negative_input_id'].to(config.device)
                # negative_attention_mask = batch['negative_attention_mask'].to(config.device)
                
                embeddings = model(input_ids,attention_mask)
                # po_em = model(possitive_input_ids,possitive_attention_mask)
                # ne_em = model(negative_input_id,negative_attention_mask)
            
                loss = criterion(embeddings, labels)
                total_valid_loss += loss.item()
                
        avg_train_loss = total_train_loss / len(train_dataloader)
        avg_valid_loss = total_valid_loss / len(val_dataloader)
        
        valid_losses.append(avg_valid_loss)
        train_losses.append(avg_train_loss)
        
        #print(f"Epoch {epoch+1}, Train Loss: {avg_train_loss:.4f}, Valid Loss: {avg_valid_loss:.4f}")
        
        if avg_valid_loss < best_valid_loss:
            best_valid_loss = avg_valid_loss
            patience_counter = 0
            torch.save(model.state_dict(), '/home/lyb/vps_benchmark/src/NLP/model/contrastiveLearn_model.pt')
        # else:
        #     patience_counter += 1
        # if patience_counter >= config.patience:
        #     print("Early stopping triggered.")
        #     break
        
        if epoch%5 == 0:
            tsne = TSNE(n_components=2, random_state=42)
            all_embeddings = torch.cat(all_embeddings, dim=0).detach().cpu().numpy()
            embeddings_2d = tsne.fit_transform(all_embeddings)
            all_labels = torch.cat(all_labels, dim=0).numpy()

            # 画图
            plt.figure(figsize=(8, 6))
            scatter = plt.scatter(embeddings_2d[:, 0], embeddings_2d[:, 1], c=all_labels, cmap='tab10', alpha=0.6)
            plt.colorbar(scatter)
            plt.title("t-SNE of Contrastive Learned Embeddings")
            plt.savefig('contrastive_tsne_epoch_'+str(epoch)+'.png')
            plt.show()
        
    plt.figure(figsize=(8, 6))
    plt.plot(train_losses, label='Train Loss')
    plt.plot(valid_losses, label='Valid Loss')
    plt.xlabel('Epoch')
    plt.ylabel('Loss')
    plt.title('Contrastive Learning Loss Curve')
    plt.legend()
    plt.grid()
    plt.savefig('contrastive_loss_curve.png')
    plt.show()
    

                
                
                
if __name__ == "__main__":
    json_list:list
    json_list = read_file_to_json()
    scaler = StandardScaler()
    sql_list, value_list, transaction_type_list = sql2vec(json_list)
    value_array = scaler.fit_transform(np.array(value_list).reshape(-1,1)).flatten()
    # sql_array = np.array(sql_list)
    # transaction_type_array = np.array(transaction_type_list)
    x_train,x_test,y_train,y_test = train_test_split(sql_list,transaction_type_list,test_size = 0.2, random_state=42)
    x_test,x_val,y_test,y_val = train_test_split(x_test,y_test,test_size = 0.5,random_state=42)
    
    x_train = tokenizer(x_train,padding=True,truncation=True,max_length=512)
    x_test = tokenizer(x_test,padding=True,truncation=True,max_length=512)
    x_val = tokenizer(x_val,padding=True,truncation=True,max_length=512)
    
    train_dataset = SQLDataset(x_train,y_train)
    train_dataloader = DataLoader(train_dataset,batch_size=config.batch_size, shuffle=True)
    
    test_dataset = SQLDataset(x_test,y_test)
    test_dataloader = DataLoader(test_dataset,batch_size=config.batch_size, shuffle=True)
    
    val_dataset = SQLDataset(x_val,y_val)
    val_dataloader = DataLoader(val_dataset,batch_size=config.batch_size, shuffle=True)
    
    
    
    model = ContrastiveLearn(config,roberta_model).to(config.device)
    optimizer = AdamW(model.parameters(), lr=1e-4)
    
    ContrastiveLearn_learn(model, train_dataloader,val_dataloader, optimizer, config)
    
    # contrastivelearn_model = ContrastiveLearn(config,roberta_model)
    # contrastivelearn_model.load_state_dict(torch.load("/home/lyb/vps_benchmark/src/NLP/model/contrastiveLearn_model.pt"))
    # contrastivelearn_model.to(config.device)
    
    # train_embeddings = []
    # val_embeddings = []
    # test_embeddings = []
    # train_labels_array = np.array([])
    # val_labels_array = np.array([])
    # test_labels_array = np.array([])
    # print("sql2em:")
    # with torch.no_grad():
    #     for batch in train_dataloader:
    #         batch_input_ids = batch['input_ids'].to(config.device)
    #         batch_attention_mask = batch['attention_mask'].to(config.device)
    #         train_batch_labels = batch['labels'].to(config.device)
    #         embeddings = contrastivelearn_model(batch_input_ids,batch_attention_mask)
    #         train_embeddings.append(embeddings.cpu().numpy())
    #         train_labels_array = np.concatenate([train_labels_array,train_batch_labels.cpu().numpy()])
            
    #     for batch in val_dataloader:
    #         batch_input_ids = batch['input_ids'].to(config.device)
    #         batch_attention_mask = batch['attention_mask'].to(config.device)
    #         val_batch_labels = batch['labels'].to(config.device)
    #         embeddings = contrastivelearn_model(batch_input_ids,batch_attention_mask)
    #         val_embeddings.append(embeddings.cpu().numpy())
    #         val_labels_array = np.concatenate([val_labels_array,val_batch_labels.cpu().numpy()])
            
    #     for batch in test_dataloader:
    #         batch_input_ids = batch['input_ids'].to(config.device)
    #         batch_attention_mask = batch['attention_mask'].to(config.device)
    #         test_batch_labels = batch['labels'].to(config.device)
    #         embeddings = contrastivelearn_model(batch_input_ids,batch_attention_mask)
    #         test_embeddings.append(embeddings.cpu().numpy())
    #         test_labels_array = np.concatenate([test_labels_array,test_batch_labels.cpu().numpy()])
    
    # train_embeddings = np.vstack(train_embeddings)
    # val_embeddings = np.vstack(val_embeddings)
    # test_embeddings = np.vstack(test_embeddings)
    # classifer_train_data = lgb.Dataset(train_embeddings, label=train_labels_array)
    # classifer_val_data = lgb.Dataset(val_embeddings, label=val_labels_array)
    # classifer_test_data = lgb.Dataset(test_embeddings, label=test_labels_array)

    
    # params = {
    # 'objective': 'multiclass',
    # 'num_class': 5,
    # 'metric': 'multi_logloss',
    # 'learning_rate': 1e-4,
    # 'num_leaves': 31,
    # 'verbose': -1
    # }
    
    # clf = lgb.train(
    #     params,
    #     classifer_train_data,
    #     valid_sets=[classifer_val_data],
    #     num_boost_round=100,
    #     callbacks=[lgb.early_stopping(stopping_rounds=10)]
    # )
    
    # y_pred = clf.predict(test_embeddings)
    # y_pred_label = y_pred.argmax(axis = 1)
    # print(classification_report(y_test, y_pred_label))
    
            
            
    
    
    
    
            