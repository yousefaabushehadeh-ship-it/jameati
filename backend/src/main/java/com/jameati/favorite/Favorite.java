package com.jameati.favorite;
import com.jameati.common.BaseEntity;
import com.jameati.opportunity.Opportunity;
import com.jameati.user.User;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="favorites",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","opportunity_id"})) @Getter @Setter @NoArgsConstructor
public class Favorite extends BaseEntity { @ManyToOne(fetch=FetchType.LAZY,optional=false) private User user; @ManyToOne(fetch=FetchType.LAZY,optional=false) private Opportunity opportunity; }
