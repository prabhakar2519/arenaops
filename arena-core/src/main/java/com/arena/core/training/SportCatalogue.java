package com.arena.core.training;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="SPORT_CATALOGUE") @Getter @Setter
public class SportCatalogue extends TrainingRecord {
 private String code;
 private String label;
 private String facilityKind;
 private boolean active;
}
