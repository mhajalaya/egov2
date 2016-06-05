package gov.esprit.service.cin.impl;

import static gov.esprit.enums.EtatDemande.DELIVREE;
import static gov.esprit.enums.EtatDemande.EN_COURS;
import static gov.esprit.enums.EtatDemande.IRRIGULARITE;
import static gov.esprit.enums.EtatDemande.PRET;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateful;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import gov.esprit.business.CinInfo;
import gov.esprit.business.TraiterDemandeInfo;
import gov.esprit.domain.Citoyen;
import gov.esprit.domain.Demande;
import gov.esprit.enums.EtatDemande;
import gov.esprit.enums.Gouvernerat;
import gov.esprit.enums.Profession;
import gov.esprit.enums.TypeDemande;
import gov.esprit.exception.EgovErrorCode;
import gov.esprit.exception.EgovException;
import gov.esprit.service.cin.DemandeCINServiceLocal;
import gov.esprit.service.cin.DemandeCINServiceRemote;
import gov.esprit.service.citoyen.CitoyenServiceLocal;
import gov.esprit.service.demande.impl.DemandeServiceLocal;
import gov.esprit.service.user.UserServiceLocal;
import gov.esprit.utils.Identifier;

/**
 * Session Bean implementation class DemandeCINService.
 * 
 * @author monta
 */
@Stateful
@LocalBean
public class DemandeCINService implements DemandeCINServiceRemote, DemandeCINServiceLocal {

	/**
	 * {@link UserServiceLocal}.
	 */
	@EJB
	UserServiceLocal userServiceLocal;

	/**
	 * {@link DemandeServiceLocal}.
	 */
	@EJB
	DemandeServiceLocal demandeServiceLocal;
	
	/**
	 * {@link CitoyenServiceLocal}.
	 */
	@EJB
	CitoyenServiceLocal citoyenServiceLocal;

	@PersistenceContext
	private EntityManager em;

	/**
	 * Default constructor.
	 */
	public DemandeCINService() {
	}

	@Override
	public void ajouter(String nom, String prenom, Date date, boolean isInscrit, boolean isExtrait, boolean isResidenceProof, boolean isTravailProof, Gouvernerat gouvernerat)
			throws EgovException {

		Citoyen citoyen = citoyenServiceLocal.findByNomAndPrenomAndDateAndGouvernerat(nom, prenom, date, gouvernerat);
		
		if (citoyen == null) {
			throw new EgovException(EgovErrorCode.DOES_NOT_EXIST_ITEM, "_CITOYEN");
		}
		if (!isExtrait) {
			throw new EgovException(EgovErrorCode.DOES_NOT_EXIST_ITEM, "_EXTRAIT_DE_NAISSANCE");
		}
		if (!isResidenceProof) {
			throw new EgovException(EgovErrorCode.DOES_NOT_EXIST_ITEM, "_PREUVE_DE_RESIDENCE");
		}
		if (citoyen.getProfession().equals(Profession.ETDUIANT.name()) && !isInscrit){
			throw new EgovException(EgovErrorCode.DOES_NOT_EXIST_ITEM, "_ATTESTATION_INSCRIPTION");
		}
		if ( citoyen.getProfession().equals(Profession.ACTIF.name())){
			
			throw new EgovException(EgovErrorCode.DOES_NOT_EXIST_ITEM, "_ATTESTATION_TRAVAIL");
		}
		Demande demande = new Demande();
		demande.setType(TypeDemande.DEMANDE_CIN);
		demande.setDate(new Date());
		demande.setEtat(EtatDemande.EN_ATTENTE);
		demande.setCitoyen(citoyen);

		em.persist(demande);
	}

	@Override
	public CinInfo traiter(TraiterDemandeInfo info) throws EgovException {

		Demande demande = demandeServiceLocal.rechercherDemande(info.getNumDmande());
		demande.setEtapeCin(info.getEtapeCin());
		// irregularite
		if (!info.isConforme()) {
			demande.setEtat(IRRIGULARITE);
			return null;
		} 
		// delivree
		if (info.getEtapeCin().isLivraison()){
			
			return delivrer(demande);
		}
		// pret
		if (info.getEtapeCin().isDocuments() 
				&& info.getEtapeCin().isEmpreinte()
				&& info.getEtapeCin().isImpression()) {
			
			demande.setEtat(PRET);
			return null;
		}
		demande.setEtat(EN_COURS);
		return null;
	}

	private CinInfo delivrer(Demande demande) throws EgovException {
		
		CinInfo cinInfo = new CinInfo();

		// get the cin proprio
		Citoyen citoyen = demande.getCitoyen();

		cinInfo.setDateExpedition(LocalDateTime.now());

		// generate cin identifier
		String cin = Identifier.generate(citoyen.getId());
		cinInfo.setNumero(Integer.valueOf(cin));

		// accord cin reference to the citoyen
		citoyen.setCin(cin);
		cinInfo.setProprietaire(citoyen);

		cinInfo.setReferenceEmpreinte(UUID.randomUUID().toString());

		demande.setEtat(DELIVREE);
		
		em.merge(demande);

		return cinInfo;
	}

}
