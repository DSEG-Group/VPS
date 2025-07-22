import torch
import torch.nn as nn
import torch.nn.functional as F
from transformers import BertModel, BertTokenizer
from torch.utils.data import Dataset, DataLoader
import numpy as np



class ContrastiveLearn(nn.Module):
    def __init__(self,config,roberta_model):
        super().__init__()
        self.roberta = roberta_model.to(config.device)
        
        for name, param in self.roberta.named_parameters():
            if 'encoder.layer.10' in name or 'encoder.layer.11' in name:
                param.requires_grad = True
            else:
                param.requires_grad = False
                
        self.projector = nn.Sequential(
            nn.Linear(768,256),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(256,config.projection_dim)
        )
        
    def forward(self, input_ids, attention_mask):
        outputs = self.roberta(input_ids=input_ids, attention_mask=attention_mask)
        cls_embedding = outputs.last_hidden_state[:,0,:]
        projected = self.projector(cls_embedding)
        return F.normalize(projected, dim = 1)
    
class SupConLoss(nn.Module):
    def __init__(self, temperature=0.1):
        super(SupConLoss, self).__init__()
        self.temperature = temperature

    def forward(self, features, labels):
        device = (torch.device('cuda') if features.is_cuda else torch.device('cpu'))
        batch_size = features.shape[0]
        mask = torch.eq(labels.unsqueeze(1), labels.unsqueeze(0)).float().to(device)

        contrast = torch.div(torch.matmul(features, features.T), self.temperature)
        logits_max, _ = torch.max(contrast, dim=1, keepdim=True)
        logits = contrast - logits_max.detach()

        exp_logits = torch.exp(logits) * (1 - torch.eye(batch_size).to(device))
        log_prob = logits - torch.log(exp_logits.sum(1, keepdim=True))

        mean_log_prob_pos = (mask * log_prob).sum(1) / mask.sum(1)

        loss = -mean_log_prob_pos
        loss = loss.mean()
        return loss
    

class SQLDataset(Dataset):
    def __init__(self, encodings, labels):
        self.encodings = encodings
        self.labels = labels
        
        self.label_to_indices = {}
        for idx, label in enumerate(labels):
            if label not in self.label_to_indices:
                self.label_to_indices[label] = []
            self.label_to_indices[label].append(idx)
    
    def __len__(self):
        return len(self.labels)
    
    def __getitem__(self, idx):
        
        positive_idx = idx
        anchor_label =torch.tensor(self.labels[idx], dtype=torch.long)
        anchor_input_id = torch.tensor(self.encodings['input_ids'][idx],dtype=torch.long)
        anchor_attention_mask = torch.tensor(self.encodings['attention_mask'][idx],dtype=torch.long)
        
        while positive_idx == idx:
            positive_idx = np.random.choice(self.label_to_indices[self.labels[idx]])
            positive_input_id = torch.tensor(self.encodings['input_ids'][positive_idx], dtype=torch.long)
            positive_attention_mask = torch.tensor(self.encodings['attention_mask'][positive_idx], dtype=torch.long)

    # Negative
        negative_label = anchor_label
        while negative_label == anchor_label:
            negative_label = np.random.choice(list(self.label_to_indices.keys()))
            negative_idx = np.random.choice(self.label_to_indices[negative_label])
            negative_input_id = torch.tensor(self.encodings['input_ids'][negative_idx], dtype=torch.long)
            negative_attention_mask = torch.tensor(self.encodings['attention_mask'][negative_idx], dtype=torch.long)
        
        
        item = {
            'input_ids': anchor_input_id,
            'attention_mask': anchor_attention_mask,
            'labels': anchor_label,
            'anchor_input_id': anchor_input_id,
            'anchor_attention_mask': anchor_attention_mask,
            'positive_input_id': positive_input_id,
            'positive_attention_mask': positive_attention_mask,
            'negative_input_id': negative_input_id,
            'negative_attention_mask': negative_attention_mask
        }
        return item
    
class TripletLoss(nn.Module):
    def __init__(self, margin=0.3):
        super(TripletLoss, self).__init__()
        self.margin = margin
        self.ranking_loss = nn.MarginRankingLoss(margin=margin)

    def forward(self, anchor, positive, negative):
        d_pos = (anchor - positive).pow(2).sum(1)
        d_neg = (anchor - negative).pow(2).sum(1)
        target = torch.ones_like(d_pos)
        loss = self.ranking_loss(d_neg, d_pos, target)
        return loss

    